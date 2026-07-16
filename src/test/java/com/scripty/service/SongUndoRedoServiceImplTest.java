package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SongUndoRedoServiceImplTest {

    private static final Integer DOC_ID = 7;

    private SongBlockService songBlockService;
    private SongUndoRedoServiceImpl service;

    /** Stands in for the persisted song: snapshots read it, undo/redo write it. */
    private List<String> lines;

    @BeforeEach
    void setUp() {
        songBlockService = mock(SongBlockService.class);
        service = new SongUndoRedoServiceImpl(songBlockService, new ObjectMapper());

        lines = new ArrayList<>(List.of("one"));
        when(songBlockService.snapshotLines(DOC_ID)).thenAnswer(i -> new ArrayList<>(lines));
        doAnswerReplace();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @SuppressWarnings("unchecked")
    private void doAnswerReplace() {
        org.mockito.Mockito.doAnswer(i -> {
            lines = new ArrayList<>((List<String>) i.getArgument(1));
            return null;
        }).when(songBlockService).replaceLines(eq(DOC_ID), org.mockito.ArgumentMatchers.anyList());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void undoRestoresTheSnapshotTakenBeforeTheChange() {
        service.recordCheckpoint(DOC_ID);
        lines = new ArrayList<>(List.of("one edited", "two"));

        assertTrue(service.canUndo(DOC_ID));
        assertTrue(service.undo(DOC_ID));
        assertEquals(List.of("one"), lines);
    }

    @Test
    void redoReappliesWhatUndoReverted() {
        service.recordCheckpoint(DOC_ID);
        lines = new ArrayList<>(List.of("one edited"));

        service.undo(DOC_ID);
        assertEquals(List.of("one"), lines);

        assertTrue(service.canRedo(DOC_ID));
        assertTrue(service.redo(DOC_ID));
        assertEquals(List.of("one edited"), lines);
    }

    @Test
    void undoWalksBackThroughSuccessiveCheckpoints() {
        service.recordCheckpoint(DOC_ID);
        lines = new ArrayList<>(List.of("first"));
        service.recordCheckpoint(DOC_ID);
        lines = new ArrayList<>(List.of("second"));

        service.undo(DOC_ID);
        assertEquals(List.of("first"), lines);
        service.undo(DOC_ID);
        assertEquals(List.of("one"), lines);

        assertFalse(service.canUndo(DOC_ID));
        assertFalse(service.undo(DOC_ID));
    }

    @Test
    void aNewCheckpointDropsTheRedoStack() {
        service.recordCheckpoint(DOC_ID);
        lines = new ArrayList<>(List.of("one edited"));
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
        assertEquals(List.of("one"), lines);
    }

    @Test
    void stacksAreKeptPerDocument() {
        Integer otherDoc = 8;
        when(songBlockService.snapshotLines(otherDoc)).thenReturn(new ArrayList<>(List.of("other")));

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
}
