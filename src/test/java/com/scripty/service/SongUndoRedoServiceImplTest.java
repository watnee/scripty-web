package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.SongEdition;
import com.scripty.dto.SongUndoState;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.SongEditionRepository;
import com.scripty.repository.SongUndoStateRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.repository.UserRepository;
import com.scripty.service.SongBlockService.LineSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The stacks themselves, and — the reason they are persisted at all — that they
 * are still there for a client that keeps no session between requests.
 */
class SongUndoRedoServiceImplTest {

    private static final Integer DOC_ID = 7;
    private static final Integer EDITION_ID = 100;
    private static final Integer USER_ID = 3;
    private static final String USERNAME = "writer";

    private SongBlockService songBlockService;
    private SongUndoRedoServiceImpl service;

    /** Stands in for the persisted song: snapshots read it, undo/redo write it. */
    private List<LineSnapshot> lines;

    /** Stands in for {@code song_undo_state}, keyed as the unique constraint is. */
    private Map<String, SongUndoState> rows;

    /** Stands in for {@code user}: whoever has been signed in during a test. */
    private final Map<String, User> users = new HashMap<>();

    /** An untinted line, the common case in these tests. */
    private static LineSnapshot line(String content) {
        return new LineSnapshot(content, null);
    }

    private static List<LineSnapshot> lines(LineSnapshot... entries) {
        return new ArrayList<>(Arrays.asList(entries));
    }

    @BeforeEach
    void setUp() {
        songBlockService = mock(SongBlockService.class);
        service = new SongUndoRedoServiceImpl(songBlockService, new ObjectMapper(),
                stateRepository(), documentRepository(), editionRepository(), userRepository());

        lines = lines(line("one"));
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenAnswer(i -> new ArrayList<>(lines));
        doAnswerReplace();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @SuppressWarnings("unchecked")
    private void doAnswerReplace() {
        org.mockito.Mockito.doAnswer(i -> {
            lines = new ArrayList<>((List<LineSnapshot>) i.getArgument(2));
            return null;
        }).when(songBlockService).replaceLines(eq(DOC_ID), eq(EDITION_ID), org.mockito.ArgumentMatchers.anyList());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    // --- the stacks -------------------------------------------------------

    @Test
    void undoRestoresTheSnapshotTakenBeforeTheChange() {
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("one edited"), line("two"));

        assertTrue(service.canUndo(DOC_ID, EDITION_ID));
        assertTrue(service.undo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void undoRestoresHighlightsAlongWithTheText() {
        lines = lines(new LineSnapshot("chorus", "YELLOW"));
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(new LineSnapshot("chorus edited", null));

        assertTrue(service.undo(DOC_ID, EDITION_ID));
        assertEquals(List.of(new LineSnapshot("chorus", "YELLOW")), lines);
    }

    @Test
    void redoReappliesWhatUndoReverted() {
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("one edited"));

        service.undo(DOC_ID, EDITION_ID);
        assertEquals(List.of(line("one")), lines);

        assertTrue(service.canRedo(DOC_ID, EDITION_ID));
        assertTrue(service.redo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one edited")), lines);
    }

    @Test
    void undoWalksBackThroughSuccessiveCheckpoints() {
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("first"));
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("second"));

        service.undo(DOC_ID, EDITION_ID);
        assertEquals(List.of(line("first")), lines);
        service.undo(DOC_ID, EDITION_ID);
        assertEquals(List.of(line("one")), lines);

        assertFalse(service.canUndo(DOC_ID, EDITION_ID));
        assertFalse(service.undo(DOC_ID, EDITION_ID));
    }

    @Test
    void aNewCheckpointDropsTheRedoStack() {
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("one edited"));
        service.undo(DOC_ID, EDITION_ID);
        assertTrue(service.canRedo(DOC_ID, EDITION_ID));

        service.recordCheckpoint(DOC_ID, EDITION_ID);

        assertFalse(service.canRedo(DOC_ID, EDITION_ID));
        assertFalse(service.redo(DOC_ID, EDITION_ID));
    }

    @Test
    void undoAndRedoAreNoOpsWithNothingRecorded() {
        assertFalse(service.canUndo(DOC_ID, EDITION_ID));
        assertFalse(service.canRedo(DOC_ID, EDITION_ID));
        assertFalse(service.undo(DOC_ID, EDITION_ID));
        assertFalse(service.redo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void stacksAreKeptPerDocument() {
        Integer otherDoc = 8;
        when(songBlockService.snapshotLines(otherDoc, EDITION_ID)).thenReturn(lines(line("other")));

        service.recordCheckpoint(DOC_ID, EDITION_ID);

        assertTrue(service.canUndo(DOC_ID, EDITION_ID));
        assertFalse(service.canUndo(otherDoc, EDITION_ID));
    }

    @Test
    void checkpointForBlockResolvesTheOwningDocument() {
        when(songBlockService.documentIdForBlock(99)).thenReturn(DOC_ID);
        when(songBlockService.editionIdForBlock(99)).thenReturn(EDITION_ID);

        service.recordCheckpointForBlock(99);

        assertTrue(service.canUndo(DOC_ID, EDITION_ID));
    }

    @Test
    void checkpointIsSkippedForAnUnknownDocument() {
        when(songBlockService.snapshotLines(404, EDITION_ID)).thenReturn(null);

        service.recordCheckpoint(404, EDITION_ID);

        assertFalse(service.canUndo(404, EDITION_ID));
    }

    // --- outliving the session --------------------------------------------
    //
    // The bug these were written for: the iOS client refuses cookies, so every
    // request it makes lands in a session of its own. A stack held in session
    // attributes was therefore empty on the request that asked to undo — the
    // status endpoint answered canUndo:false forever and the editor's Undo
    // button never came out of its greyed state.

    @Test
    void aCheckpointSurvivesAClientThatKeepsNoSession() {
        givenSignedInWriter();
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("one edited"));

        newRequest();

        assertTrue(service.canUndo(DOC_ID, EDITION_ID));
        assertTrue(service.undo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void redoSurvivesAClientThatKeepsNoSession() {
        givenSignedInWriter();
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("one edited"));
        service.undo(DOC_ID, EDITION_ID);

        newRequest();

        assertTrue(service.canRedo(DOC_ID, EDITION_ID));
        assertTrue(service.redo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one edited")), lines);
    }

    /**
     * A stack read back out of storage must pop in the order it was pushed —
     * newest step first — or undo walks the writer's history backwards.
     */
    @Test
    void aRestoredStackKeepsItsOrder() {
        givenSignedInWriter();
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("first"));
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("second"));

        newRequest();

        assertTrue(service.undo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("first")), lines);
        assertTrue(service.undo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one")), lines);
        assertFalse(service.canUndo(DOC_ID, EDITION_ID));
    }

    /** A collaborator's undo rewinds their own edits, not this writer's. */
    @Test
    void stacksAreKeptPerWriter() {
        givenSignedInWriter();
        service.recordCheckpoint(DOC_ID, EDITION_ID);

        signIn("other", 4);

        assertFalse(service.canUndo(DOC_ID, EDITION_ID));

        signIn(USERNAME, USER_ID);

        assertTrue(service.canUndo(DOC_ID, EDITION_ID));
    }

    /**
     * Nobody signed in — an unauthenticated context, or a dev auto-login
     * principal with no row in {@code user} — still gets the session stack
     * rather than no undo at all.
     */
    @Test
    void anUnresolvedUserFallsBackToTheSession() {
        service.recordCheckpoint(DOC_ID, EDITION_ID);
        lines = lines(line("one edited"));

        assertTrue(service.canUndo(DOC_ID, EDITION_ID));
        assertTrue(service.undo(DOC_ID, EDITION_ID));
        assertEquals(List.of(line("one")), lines);
        assertTrue(rows.isEmpty());
    }

    // --- the fakes --------------------------------------------------------

    /** A fresh request with a session of its own, as every API call gets. */
    private void newRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private void givenSignedInWriter() {
        signIn(USERNAME, USER_ID);
    }

    private void signIn(String username, Integer userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
        users.put(username, user(username, userId));
    }

    private static User user(String username, Integer id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private SongUndoStateRepository stateRepository() {
        rows = new HashMap<>();
        SongUndoStateRepository repository = mock(SongUndoStateRepository.class);
        when(repository.findByTextDocumentIdAndSongEditionIdAndUserId(any(), any(), any()))
                .thenAnswer(i -> Optional.ofNullable(
                        rows.get(key(i.getArgument(0), i.getArgument(1), i.getArgument(2)))));
        when(repository.save(any(SongUndoState.class))).thenAnswer(i -> {
            SongUndoState row = i.getArgument(0);
            rows.put(key(row.getTextDocument().getId(), row.getSongEdition().getId(), row.getUser().getId()), row);
            return row;
        });
        // canUndo/canRedo ask the database for the encoded stack's length rather
        // than reading the stack itself, so the stand-in answers that too — off
        // the same rows, which is what makes it a stand-in and not a second story.
        when(repository.findUndoStackLength(any(), any(), any()))
                .thenAnswer(i -> lengthOf(i, SongUndoState::getUndoJson));
        when(repository.findRedoStackLength(any(), any(), any()))
                .thenAnswer(i -> lengthOf(i, SongUndoState::getRedoJson));
        return repository;
    }

    private Optional<Integer> lengthOf(org.mockito.invocation.InvocationOnMock invocation,
                                       java.util.function.Function<SongUndoState, String> stack) {
        SongUndoState row = rows.get(key(invocation.getArgument(0),
                invocation.getArgument(1), invocation.getArgument(2)));
        if (row == null) {
            return Optional.empty();
        }
        String json = stack.apply(row);
        return Optional.of(json != null ? json.length() : 0);
    }

    private static String key(Integer documentId, Integer editionId, Integer userId) {
        return documentId + "_" + editionId + "_" + userId;
    }

    private TextDocumentRepository documentRepository() {
        TextDocumentRepository repository = mock(TextDocumentRepository.class);
        when(repository.findById(any())).thenAnswer(i -> {
            TextDocument document = new TextDocument();
            document.setId(i.getArgument(0));
            return Optional.of(document);
        });
        return repository;
    }

    private SongEditionRepository editionRepository() {
        SongEditionRepository repository = mock(SongEditionRepository.class);
        when(repository.findById(any())).thenAnswer(i -> {
            SongEdition edition = new SongEdition();
            edition.setId(i.getArgument(0));
            return Optional.of(edition);
        });
        return repository;
    }

    private UserRepository userRepository() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByUsername(any())).thenAnswer(i -> Optional.ofNullable(users.get(i.getArgument(0))));
        when(repository.findById(any())).thenAnswer(i -> users.values().stream()
                .filter(u -> u.getId().equals(i.getArgument(0)))
                .findFirst());
        return repository;
    }
}
