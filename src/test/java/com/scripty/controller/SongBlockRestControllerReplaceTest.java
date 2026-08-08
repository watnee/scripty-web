package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.ReplaceOccurrenceRequest;
import com.scripty.api.SongBlockResource;
import com.scripty.api.SongBlockResourceAssembler;
import com.scripty.api.SongBulkReplaceRequest;
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Find and replace across a song's lyric lines — the song twin of
 * {@link BlockRestControllerReplaceTest}.
 *
 * <p>The load-bearing assertion is
 * {@link #replaceAllTakesExactlyOneCheckpointForTheWholeSweep()}: one checkpoint
 * for the whole sweep is the entire reason this went through the server rather
 * than being faked with one update per line on the client. A per-line checkpoint
 * would leave a writer pressing Undo once for every line a Replace All touched.
 */
class SongBlockRestControllerReplaceTest {

    private static final int BLOCK_ID = 3;
    private static final int DOCUMENT_ID = 11;
    private static final int EDITION_ID = 4;
    private static final int PROJECT_ID = 7;

    private final SongBlockRestController controller = new SongBlockRestController();
    private final SongBlockService songBlockService = mock(SongBlockService.class);
    private final SongEditionService songEditionService = mock(SongEditionService.class);
    private final SongBlockResourceAssembler assembler = mock(SongBlockResourceAssembler.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final SongUndoRedoService songUndoRedoService = mock(SongUndoRedoService.class);
    private final SongVersionService songVersionService = mock(SongVersionService.class);
    private final Principal principal = () -> "writer";

    @BeforeEach
    void setUp() {
        controller.songBlockService = songBlockService;
        controller.songEditionService = songEditionService;
        controller.assembler = assembler;
        controller.projectAccess = projectAccess;
        controller.songUndoRedoService = songUndoRedoService;
        controller.songVersionService = songVersionService;
    }

    private SongBlock block() {
        SongBlock block = new SongBlock();
        block.setId(BLOCK_ID);
        return block;
    }

    private void allowLineEdit(SongBlock block) {
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(PROJECT_ID, principal)).thenReturn(true);
        when(songBlockService.read(BLOCK_ID)).thenReturn(block);
        when(assembler.toModel(any(SongBlock.class), any()))
                .thenReturn(EntityModel.of(new SongBlockResource()));
    }

    private void allowSongEdit() {
        SongEdition edition = new SongEdition();
        edition.setId(EDITION_ID);
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(PROJECT_ID, principal)).thenReturn(true);
        when(songEditionService.requireForDocument(DOCUMENT_ID, null)).thenReturn(edition);
        when(songEditionService.requireForDocument(DOCUMENT_ID, EDITION_ID)).thenReturn(edition);
        when(songBlockService.getBlocks(DOCUMENT_ID, EDITION_ID)).thenReturn(List.of());
        when(assembler.toCollection(anyList(), any(), any(), any()))
                .thenReturn(CollectionModel.of(List.of()));
    }

    private ReplaceOccurrenceRequest lineRequest(String find, int occurrence) {
        return new ReplaceOccurrenceRequest(find, "moon", false, false, occurrence);
    }

    private SongBulkReplaceRequest songRequest(String find, List<Integer> ids) {
        return new SongBulkReplaceRequest(ids, find, "moon", false, false);
    }

    // ---- single replace ----

    @Test
    void replacesTheNamedOccurrenceAndCheckpointsTheLine() {
        SongBlock block = block();
        allowLineEdit(block);
        when(songBlockService.replaceOccurrenceInBlock(BLOCK_ID, "sun", "moon", false, false, 1))
                .thenReturn(block);

        ResponseEntity<?> response = controller.replace(BLOCK_ID, lineRequest("sun", 1), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songUndoRedoService).recordCheckpointForBlock(BLOCK_ID);
        verify(songBlockService).replaceOccurrenceInBlock(BLOCK_ID, "sun", "moon", false, false, 1);
        verify(songVersionService).autoSaveVersionForBlock(BLOCK_ID);
    }

    @Test
    void aMissingOccurrenceDefaultsToTheFirst() {
        SongBlock block = block();
        allowLineEdit(block);
        when(songBlockService.replaceOccurrenceInBlock(BLOCK_ID, "sun", "moon", false, false, 0))
                .thenReturn(block);

        ResponseEntity<?> response = controller.replace(
                BLOCK_ID, new ReplaceOccurrenceRequest("sun", "moon", null, null, null), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songBlockService).replaceOccurrenceInBlock(BLOCK_ID, "sun", "moon", false, false, 0);
    }

    @Test
    void aNoOpOccurrenceStillReturnsTheLineWithoutAutosaving() {
        SongBlock block = block();
        allowLineEdit(block);
        when(songBlockService.replaceOccurrenceInBlock(
                eq(BLOCK_ID), any(), any(), anyBool(), anyBool(), anyInt())).thenReturn(null);

        ResponseEntity<?> response = controller.replace(BLOCK_ID, lineRequest("sun", 9), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songVersionService, never()).autoSaveVersionForBlock(anyInt());
    }

    @Test
    void rejectsAnEmptyFindOnALine() {
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(PROJECT_ID, principal)).thenReturn(true);

        ResponseEntity<?> response = controller.replace(
                BLOCK_ID, new ReplaceOccurrenceRequest("", "moon", false, false, 0), principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("You must supply a value to find.", ((Map<?, ?>) response.getBody()).get("find"));
        verify(songBlockService, never()).replaceOccurrenceInBlock(
                anyInt(), any(), any(), anyBool(), anyBool(), anyInt());
    }

    @Test
    void forbidsANonEditorOnALine() {
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(PROJECT_ID, principal)).thenReturn(false);

        ResponseEntity<?> response = controller.replace(BLOCK_ID, lineRequest("sun", 0), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(songBlockService, never()).replaceOccurrenceInBlock(
                anyInt(), any(), any(), anyBool(), anyBool(), anyInt());
    }

    // ---- replace all ----

    /**
     * The whole point of putting Replace All on the server: however many lines it
     * rewrites, a writer takes it back with one Undo.
     */
    @Test
    void replaceAllTakesExactlyOneCheckpointForTheWholeSweep() {
        allowSongEdit();
        when(songBlockService.replaceInLines(
                eq(DOCUMENT_ID), eq(EDITION_ID), any(), eq("sun"), eq("moon"), anyBool(), anyBool()))
                .thenReturn(6);

        ResponseEntity<?> response = controller.bulkReplace(DOCUMENT_ID, EDITION_ID, songRequest("sun", null), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songUndoRedoService, times(1)).recordCheckpoint(DOCUMENT_ID, EDITION_ID);
        verify(songUndoRedoService, never()).recordCheckpointForBlock(anyInt());
        verify(songBlockService).replaceInLines(
                DOCUMENT_ID, EDITION_ID, null, "sun", "moon", false, false);
        verify(songVersionService).autoSaveVersion(DOCUMENT_ID, EDITION_ID);
    }

    @Test
    void aNarrowedReplaceAllPassesItsIdsThrough() {
        allowSongEdit();

        ResponseEntity<?> response = controller.bulkReplace(
                DOCUMENT_ID, EDITION_ID, songRequest("sun", List.of(1, 2)), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songBlockService).replaceInLines(
                DOCUMENT_ID, EDITION_ID, List.of(1, 2), "sun", "moon", false, false);
    }

    /** A missing version resolves to the song's default, as every other route does. */
    @Test
    void anAbsentEditionResolvesToTheDefault() {
        allowSongEdit();

        ResponseEntity<?> response = controller.bulkReplace(
                DOCUMENT_ID, null, new SongBulkReplaceRequest(null, "sun", "moon", null, null),
                principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(songUndoRedoService).recordCheckpoint(DOCUMENT_ID, EDITION_ID);
        verify(songBlockService).replaceInLines(
                DOCUMENT_ID, EDITION_ID, null, "sun", "moon", false, false);
    }

    @Test
    void rejectsAnEmptyFindOnReplaceAll() {
        allowSongEdit();

        ResponseEntity<?> response = controller.bulkReplace(DOCUMENT_ID, EDITION_ID, songRequest("", null), principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("You must supply a value to find.", ((Map<?, ?>) response.getBody()).get("find"));
        verify(songUndoRedoService, never()).recordCheckpoint(anyInt(), anyInt());
    }

    /** An empty body is the same refusal an empty {@code find} gets. */
    @Test
    void rejectsAMissingBody() {
        allowSongEdit();

        ResponseEntity<?> response = controller.bulkReplace(DOCUMENT_ID, EDITION_ID, null, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("You must supply a value to find.", ((Map<?, ?>) response.getBody()).get("find"));
        verify(songUndoRedoService, never()).recordCheckpoint(anyInt(), anyInt());
    }

    @Test
    void forbidsANonEditorOnReplaceAll() {
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
        when(projectAccess.canEditScript(PROJECT_ID, principal)).thenReturn(false);

        ResponseEntity<?> response = controller.bulkReplace(DOCUMENT_ID, EDITION_ID, songRequest("sun", null), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(songBlockService, never()).replaceInLines(
                anyInt(), anyInt(), any(), any(), any(), anyBool(), anyBool());
        verify(songUndoRedoService, never()).recordCheckpoint(anyInt(), anyInt());
    }

    private static boolean anyBool() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
