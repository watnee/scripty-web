package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Song undo/redo against a real database and the real schema, across the
 * request boundary every API client puts between typing and undoing.
 *
 * <p>The bug this pins: the stacks used to live in session attributes. The iOS
 * client refuses cookies outright, so each of its requests landed in a session
 * of its own and the stack was rebuilt empty on the one that asked to undo —
 * {@code undo-redo-status} answered {@code canUndo:false} forever and the song
 * editor's Undo button never came out of its greyed state. The browser, which
 * keeps a session, never saw it.
 *
 * <p>Which is why {@link #newRequest} is what makes these tests bite. Spring's
 * {@code ServletTestExecutionListener} binds one mock request — and so one
 * session — for the whole of a test method, so a checkpoint recorded at the top
 * would still be reachable through the session fallback at the bottom and the
 * test would pass either way. Rebinding a fresh request in the middle is what a
 * second HTTP call looks like from in here.
 *
 * <p>{@link SongUndoRedoServiceImpl}'s own tests cover the stack behaviour
 * against mocks; this covers the parts only a database has — the migration, the
 * mapping and the derived query that carry a stack between those requests.
 */
@SpringBootTest
@ActiveProfiles("test")
class SongUndoRedoPersistenceIntegrationTest {

    @Autowired
    private SongUndoRedoService undoRedoService;

    @Autowired
    private SongBlockService songBlockService;

    @Autowired
    private SongEditionService songEditionService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TextDocumentRepository textDocumentRepository;

    private Integer documentId;
    private Integer editionId;
    private Integer blockId;

    @BeforeEach
    void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** A one-line song, as the editor would have it open. */
    private void givenASong(String firstLine) {
        Project project = new Project();
        project.setTitle("Undo Songs");
        Project saved = projectRepository.save(project);

        TextDocument document = new TextDocument();
        document.setTitle("A song");
        document.setDocumentType(TextDocument.TYPE_SONG);
        document.setProject(saved);
        // Stamped by the services that create documents, not by the entity.
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentId = textDocumentRepository.save(document).getId();

        editionId = songEditionService.ensureDefaultEdition(documentId).getId();
        // First access seeds the one empty line a song always has; that is the
        // line these tests type into, so the song stays one line long and a
        // restored snapshot is easy to read.
        blockId = songBlockService.getBlocks(documentId, editionId).get(0).getId();
        songBlockService.editContent(blockId, firstLine);
    }

    private List<String> currentLines() {
        return songBlockService.snapshotLines(documentId, editionId).stream()
                .map(SongBlockService.LineSnapshot::content)
                .toList();
    }

    private void type(String line) {
        songBlockService.editContent(blockId, line);
    }

    /**
     * A fresh request with a session of its own — what the next call from a
     * cookie-less client arrives on, and the only thing standing between these
     * tests and passing for the wrong reason.
     */
    private void newRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    void aCheckpointIsStillThereWithNoSessionToHoldIt() {
        givenASong("the first line");

        undoRedoService.recordCheckpointForBlock(blockId);
        type("the first line, rewritten");
        assertEquals(List.of("the first line, rewritten"), currentLines());

        newRequest();

        assertTrue(undoRedoService.canUndo(documentId, editionId),
                "a checkpoint must outlive the request that recorded it");
        assertTrue(undoRedoService.undo(documentId, editionId));
        assertEquals(List.of("the first line"), currentLines());
    }

    @Test
    void redoIsStillThereWithNoSessionToHoldIt() {
        givenASong("the first line");

        undoRedoService.recordCheckpoint(documentId, editionId);
        type("the first line, rewritten");
        undoRedoService.undo(documentId, editionId);
        assertEquals(List.of("the first line"), currentLines());

        newRequest();

        assertTrue(undoRedoService.canRedo(documentId, editionId));
        assertTrue(undoRedoService.redo(documentId, editionId));
        assertEquals(List.of("the first line, rewritten"), currentLines());
    }

    /**
     * A stack that has been undone all the way back reads as empty.
     *
     * <p>The row is still there — it is the emptied stack inside it that has to
     * answer no. {@code canUndo} does not read the stack any more; it asks the
     * database how long the encoded one is and calls {@code []} empty, so this
     * is the case that boundary exists for. Nothing else here undoes a song
     * down to nothing, and a row left over from an earlier edit would have made
     * the Undo button live with nothing behind it.
     */
    @Test
    void anEmptiedStackReadsAsEmpty() {
        givenASong("the first line");

        undoRedoService.recordCheckpoint(documentId, editionId);
        type("the first line, rewritten");
        assertTrue(undoRedoService.undo(documentId, editionId));

        newRequest();

        assertFalse(undoRedoService.canUndo(documentId, editionId),
                "the last step has been undone, so there is nothing left to undo");
        assertTrue(undoRedoService.canRedo(documentId, editionId),
                "and the step that was undone is waiting to be redone");
    }

    /** One song's history is not another's, now that they share a table. */
    @Test
    void stacksAreKeptPerSong() {
        givenASong("the first line");
        Integer firstDocument = documentId;
        Integer firstEdition = editionId;
        undoRedoService.recordCheckpoint(firstDocument, firstEdition);

        givenASong("a different song");
        newRequest();

        assertFalse(undoRedoService.canUndo(documentId, editionId));
        assertTrue(undoRedoService.canUndo(firstDocument, firstEdition));
    }
}
