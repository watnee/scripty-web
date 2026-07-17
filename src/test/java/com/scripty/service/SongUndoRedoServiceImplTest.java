package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.service.SongBlockService.LineSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SongUndoRedoServiceImplTest {

    private static final Integer DOC_ID = 7;
    private static final Integer EDITION_ID = 100;

    private SongBlockService songBlockService;
    private SongUndoRedoServiceImpl service;

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
        service = new SongUndoRedoServiceImpl(songBlockService, new ObjectMapper());

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
    }

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
}
