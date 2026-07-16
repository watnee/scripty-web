package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.SongUndoState;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
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

class SongUndoRedoServiceImplTest {

    private static final Integer DOC_ID = 7;
    private static final Integer USER_ID = 3;
    private static final Integer OTHER_USER_ID = 4;

    private SongBlockService songBlockService;
    private SongUndoRedoServiceImpl service;

    /** Stands in for the song_undo_state table, keyed the way the unique index is. */
    private Map<String, SongUndoState> storedStacks;

    /** Stands in for the persisted song: snapshots read it, undo/redo write it. */
    private List<LineSnapshot> lines;

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
        storedStacks = new HashMap<>();
        service = new SongUndoRedoServiceImpl(songBlockService, new ObjectMapper(),
                stubStackRepository(), stubDocumentRepository(), stubUserRepository());

        lines = lines(line("one"));
        when(songBlockService.snapshotLines(DOC_ID)).thenAnswer(i -> new ArrayList<>(lines));
        doAnswerReplace();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    /** An in-memory stand-in for the stack table, keyed (document, user). */
    private SongUndoStateRepository stubStackRepository() {
        SongUndoStateRepository repository = mock(SongUndoStateRepository.class);
        when(repository.findByTextDocumentIdAndUserId(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> Optional.ofNullable(storedStacks.get(key(i.getArgument(0), i.getArgument(1)))));
        when(repository.save(org.mockito.ArgumentMatchers.any(SongUndoState.class))).thenAnswer(i -> {
            SongUndoState row = i.getArgument(0);
            storedStacks.put(key(row.getTextDocument().getId(), row.getUser().getId()), row);
            return row;
        });
        return repository;
    }

    private TextDocumentRepository stubDocumentRepository() {
        TextDocumentRepository repository = mock(TextDocumentRepository.class);
        when(repository.findById(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> {
            TextDocument document = new TextDocument();
            document.setId(i.getArgument(0));
            return Optional.of(document);
        });
        return repository;
    }

    private UserRepository stubUserRepository() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findById(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> Optional.of(user(i.getArgument(0))));
        when(repository.findByUsername("writer")).thenReturn(Optional.of(user(USER_ID)));
        when(repository.findByUsername("collaborator")).thenReturn(Optional.of(user(OTHER_USER_ID)));
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());
        return repository;
    }

    private static User user(Integer id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static String key(Integer documentId, Integer userId) {
        return documentId + ":" + userId;
    }

    /** Signs in, the way a request with a resolvable user would. */
    private void signIn(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    /** Drops the session, the way an API client that keeps no cookies does. */
    private void withoutSession() {
        RequestContextHolder.resetRequestAttributes();
    }

    @SuppressWarnings("unchecked")
    private void doAnswerReplace() {
        org.mockito.Mockito.doAnswer(i -> {
            lines = new ArrayList<>((List<LineSnapshot>) i.getArgument(1));
            return null;
        }).when(songBlockService).replaceLines(eq(DOC_ID), org.mockito.ArgumentMatchers.anyList());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void undoRestoresTheSnapshotTakenBeforeTheChange() {
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"), line("two"));

        assertTrue(service.canUndo(DOC_ID));
        assertTrue(service.undo(DOC_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void undoRestoresHighlightsAlongWithTheText() {
        lines = lines(new LineSnapshot("chorus", "YELLOW"));
        service.recordCheckpoint(DOC_ID);
        lines = lines(new LineSnapshot("chorus edited", null));

        assertTrue(service.undo(DOC_ID));
        assertEquals(List.of(new LineSnapshot("chorus", "YELLOW")), lines);
    }

    @Test
    void redoReappliesWhatUndoReverted() {
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"));

        service.undo(DOC_ID);
        assertEquals(List.of(line("one")), lines);

        assertTrue(service.canRedo(DOC_ID));
        assertTrue(service.redo(DOC_ID));
        assertEquals(List.of(line("one edited")), lines);
    }

    @Test
    void undoWalksBackThroughSuccessiveCheckpoints() {
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("first"));
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("second"));

        service.undo(DOC_ID);
        assertEquals(List.of(line("first")), lines);
        service.undo(DOC_ID);
        assertEquals(List.of(line("one")), lines);

        assertFalse(service.canUndo(DOC_ID));
        assertFalse(service.undo(DOC_ID));
    }

    @Test
    void aNewCheckpointDropsTheRedoStack() {
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"));
        service.undo(DOC_ID);
        assertTrue(service.canRedo(DOC_ID));

        service.recordCheckpoint(DOC_ID);

        assertFalse(service.canRedo(DOC_ID));
        assertFalse(service.redo(DOC_ID));
    }

    @Test
    void undoAndRedoAreNoOpsWithNothingRecorded() {
        assertFalse(service.canUndo(DOC_ID));
        assertFalse(service.canRedo(DOC_ID));
        assertFalse(service.undo(DOC_ID));
        assertFalse(service.redo(DOC_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void stacksAreKeptPerDocument() {
        Integer otherDoc = 8;
        when(songBlockService.snapshotLines(otherDoc)).thenReturn(lines(line("other")));

        service.recordCheckpoint(DOC_ID);

        assertTrue(service.canUndo(DOC_ID));
        assertFalse(service.canUndo(otherDoc));
    }

    @Test
    void checkpointForBlockResolvesTheOwningDocument() {
        when(songBlockService.documentIdForBlock(99)).thenReturn(DOC_ID);

        service.recordCheckpointForBlock(99);

        assertTrue(service.canUndo(DOC_ID));
    }

    @Test
    void checkpointIsSkippedForAnUnknownDocument() {
        when(songBlockService.snapshotLines(404)).thenReturn(null);

        service.recordCheckpoint(404);

        assertFalse(service.canUndo(404));
    }

    // --- persisted stacks -------------------------------------------------
    //
    // A signed-in user's stacks live in the database rather than the session,
    // so an API client that authenticates per request — and therefore never
    // carries a session — can still undo.

    @Test
    void undoWorksForASignedInUserWithNoSessionAtAll() {
        signIn("writer");
        withoutSession();

        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"));

        assertTrue(service.canUndo(DOC_ID));
        assertTrue(service.undo(DOC_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void redoWorksForASignedInUserWithNoSessionAtAll() {
        signIn("writer");
        withoutSession();

        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"));
        service.undo(DOC_ID);

        assertTrue(service.canRedo(DOC_ID));
        assertTrue(service.redo(DOC_ID));
        assertEquals(List.of(line("one edited")), lines);
    }

    @Test
    void aSignedInUsersStackSurvivesANewSession() {
        signIn("writer");
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"));

        // A fresh session, as after a restart or a re-login.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertTrue(service.canUndo(DOC_ID));
        assertTrue(service.undo(DOC_ID));
        assertEquals(List.of(line("one")), lines);
    }

    @Test
    void stacksAreKeptPerUser() {
        signIn("writer");
        service.recordCheckpoint(DOC_ID);
        assertTrue(service.canUndo(DOC_ID));

        // A collaborator on the same song must not inherit another's history.
        signIn("collaborator");
        assertFalse(service.canUndo(DOC_ID));
    }

    @Test
    void undoDoesNotRewindACollaboratorsEdit() {
        signIn("writer");
        service.recordCheckpoint(DOC_ID);

        signIn("collaborator");
        lines = lines(line("collaborator's line"));

        assertFalse(service.undo(DOC_ID));
        assertEquals(List.of(line("collaborator's line")), lines);
    }

    @Test
    void walkingBackThroughCheckpointsSurvivesWithoutASession() {
        signIn("writer");
        withoutSession();

        service.recordCheckpoint(DOC_ID);
        lines = lines(line("first"));
        service.recordCheckpoint(DOC_ID);
        lines = lines(line("second"));

        service.undo(DOC_ID);
        assertEquals(List.of(line("first")), lines);
        service.undo(DOC_ID);
        assertEquals(List.of(line("one")), lines);

        assertFalse(service.canUndo(DOC_ID));
    }

    @Test
    void anUnresolvableUserFallsBackToTheSession() {
        // Dev auto-login signs in as a principal with no row in `user`; undo
        // should still work for the life of the session rather than vanish.
        signIn("ghost");

        service.recordCheckpoint(DOC_ID);
        lines = lines(line("one edited"));

        assertTrue(service.canUndo(DOC_ID));
        assertTrue(service.undo(DOC_ID));
        assertEquals(List.of(line("one")), lines);
    }
}
