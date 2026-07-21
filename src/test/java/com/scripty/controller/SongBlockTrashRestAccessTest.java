package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.scripty.api.ApiRel;
import com.scripty.api.DeletedSongBlockResource;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlockViewModel;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlocksViewModel;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Deleting a lyric line over the API always soft-deleted it; until this
 * controller there was no way back, so the same delete was final from an iPad
 * and reversible from the browser. These pin the rule the web recovery page
 * follows: any member may look at what was cut, only a writer may act on it.
 */
class SongBlockTrashRestAccessTest {

    private static final Integer BLOCK_ID = 7;
    private static final Integer DOCUMENT_ID = 3;
    private static final Integer EDITION_ID = 100;
    private static final Integer PROJECT_ID = 42;

    private final SongBlockTrashRestController controller = new SongBlockTrashRestController();
    private final SongBlockService songBlockService = mock(SongBlockService.class);
    private final SongUndoRedoService songUndoRedoService = mock(SongUndoRedoService.class);
    private final SongVersionService songVersionService = mock(SongVersionService.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final Principal principal = () -> "member";

    @BeforeEach
    void setUp() {
        controller.songBlockService = songBlockService;
        controller.songUndoRedoService = songUndoRedoService;
        controller.songVersionService = songVersionService;
        controller.projectAccess = projectAccess;

        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
        when(songBlockService.editionIdForBlock(BLOCK_ID)).thenReturn(EDITION_ID);
        when(songBlockService.getDeletedBlocksViewModel(DOCUMENT_ID)).thenReturn(trash(false));
    }

    private DeletedSongBlocksViewModel trash(boolean retentionUnlimited) {
        DeletedSongBlocksViewModel viewModel = new DeletedSongBlocksViewModel();
        viewModel.setDocumentId(DOCUMENT_ID);
        viewModel.setProjectId(PROJECT_ID);
        viewModel.setRetentionUnlimited(retentionUnlimited);
        viewModel.setBlocks(List.of(new DeletedSongBlockViewModel(
                BLOCK_ID, "a line worth keeping", null,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(29))));
        return viewModel;
    }

    private void givenNonWriterMember() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(false);
    }

    private void givenWriter() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private CollectionModel<EntityModel<DeletedSongBlockResource>> body(ResponseEntity<?> response) {
        return (CollectionModel<EntityModel<DeletedSongBlockResource>>) response.getBody();
    }

    @Test
    void nonMemberCannotSeeTheTrash() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, controller.list(DOCUMENT_ID, principal).getStatusCode());
        verify(songBlockService, never()).getDeletedBlocksViewModel(anyInt());
    }

    @Test
    void memberCanReadTheTrashButIsOfferedNoWayToActOnIt() {
        givenNonWriterMember();

        ResponseEntity<?> response = controller.list(DOCUMENT_ID, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        EntityModel<DeletedSongBlockResource> line = body(response).getContent().iterator().next();
        assertFalse(line.getLink(ApiRel.RESTORE).isPresent(),
                "a reader cannot restore, so nothing should offer to");
        assertFalse(line.getLink(ApiRel.PURGE).isPresent());
        assertTrue(line.getLink(ApiRel.TRASH).isPresent());
    }

    @Test
    void writerIsOfferedRestoreAndPurge() {
        givenWriter();

        EntityModel<DeletedSongBlockResource> line =
                body(controller.list(DOCUMENT_ID, principal)).getContent().iterator().next();
        assertTrue(line.getLink(ApiRel.RESTORE).isPresent());
        assertTrue(line.getLink(ApiRel.PURGE).isPresent());
    }

    @Test
    void nonWriterCannotRestoreOrPurge() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN,
                controller.restore(BLOCK_ID, DOCUMENT_ID, principal).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                controller.purge(BLOCK_ID, DOCUMENT_ID, principal).getStatusCode());
        verify(songBlockService, never()).restoreBlock(anyInt());
        verify(songBlockService, never()).purgeBlock(anyInt());
        verifyNoInteractions(songUndoRedoService, songVersionService);
    }

    /**
     * The id names a line in the trash, which need not belong to the song the
     * caller named. Without the second check, naming a song you can edit would
     * reach a line in one you cannot.
     */
    @Test
    void aLineFromAnotherProjectIsRejectedEvenWhenTheNamedSongIsEditable() {
        givenWriter();
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(99);
        when(projectAccess.canEditScript(99, principal)).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN,
                controller.restore(BLOCK_ID, DOCUMENT_ID, principal).getStatusCode());
        verify(songBlockService, never()).restoreBlock(anyInt());
    }

    @Test
    void restoringTakesACheckpointAndSnapshotsTheVersionItCameFrom() {
        givenWriter();
        when(songBlockService.restoreBlock(BLOCK_ID)).thenReturn(DOCUMENT_ID);

        assertEquals(HttpStatus.OK, controller.restore(BLOCK_ID, DOCUMENT_ID, principal).getStatusCode());
        verify(songUndoRedoService).recordCheckpointForBlock(BLOCK_ID);
        // The edition is read before the restore moves the line, and the
        // snapshot belongs to the version the line went back into.
        verify(songVersionService).autoSaveVersion(DOCUMENT_ID, EDITION_ID);
    }

    @Test
    void restoringSomethingNoLongerThereIsNotFound() {
        givenWriter();
        when(songBlockService.restoreBlock(BLOCK_ID)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.restore(BLOCK_ID, DOCUMENT_ID, principal).getStatusCode());
        verify(songVersionService, never()).autoSaveVersion(anyInt(), any());
    }

    /**
     * Nothing will purge the line, so no date is sent. A client that read a
     * missing date as "gone soon" would rush a decision nothing is forcing.
     */
    @Test
    void unlimitedRetentionSendsNoPurgeDate() {
        givenWriter();
        when(songBlockService.getDeletedBlocksViewModel(DOCUMENT_ID)).thenReturn(trash(true));

        DeletedSongBlockResource line =
                body(controller.list(DOCUMENT_ID, principal)).getContent().iterator().next().getContent();
        assertEquals(null, line.getPurgeAt());
        assertTrue(line.getDeletedAt() != null, "when it went is known either way");
    }
}
