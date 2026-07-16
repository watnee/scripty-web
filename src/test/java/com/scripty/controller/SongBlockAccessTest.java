package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;

/**
 * The song editor locks its UI read-only for non-writers (nav.html keys off the
 * canEditScript flag set by TextDocumentController). These tests pin the
 * server-side half of that rule so the read-only UI cannot be bypassed by
 * POSTing to the endpoints directly.
 */
class SongBlockAccessTest {

    private static final Integer BLOCK_ID = 7;
    private static final Integer DOCUMENT_ID = 3;
    private static final Integer PROJECT_ID = 42;
    private static final String FORBIDDEN = "songblock/blocks :: forbidden";

    private final SongBlockController controller = new SongBlockController();
    private final SongBlockService songBlockService = mock(SongBlockService.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final SongUndoRedoService songUndoRedoService = mock(SongUndoRedoService.class);
    private final SongVersionService songVersionService = mock(SongVersionService.class);
    private final Principal principal = () -> "member";

    @BeforeEach
    void setUp() {
        controller.songBlockService = songBlockService;
        controller.projectAccess = projectAccess;
        controller.songUndoRedoService = songUndoRedoService;
        controller.songVersionService = songVersionService;

        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
    }

    /** A project member who is not a writer: access yes, script edit no. */
    private void givenNonWriterMember() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(false);
    }

    private ExtendedModelMap model() {
        return new ExtendedModelMap();
    }

    @Test
    void nonWriterCannotEditContent() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN,
                controller.edit(BLOCK_ID, "new lyrics", principal).getStatusCode());
        verifyNoInteractions(songUndoRedoService);
        verify(songBlockService, never()).editContent(anyInt(), any());
    }

    @Test
    void nonWriterCannotCreateBelow() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.createBelow(BLOCK_ID, "text", model(), principal));
        verify(songBlockService, never()).createBelow(anyInt(), any());
    }

    @Test
    void nonWriterCannotAppend() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.append(DOCUMENT_ID, model(), principal));
        verify(songBlockService, never()).appendBlock(anyInt());
    }

    @Test
    void nonWriterCannotDelete() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.delete(BLOCK_ID, model(), principal));
        verify(songBlockService, never()).deleteBlock(anyInt());
    }

    @Test
    void nonWriterCannotSetHighlight() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.setHighlight(BLOCK_ID, "yellow", model(), principal));
        verify(songBlockService, never()).setHighlight(anyInt(), any());
    }

    @Test
    void nonWriterCannotMoveUp() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.moveUp(BLOCK_ID, model(), principal));
        verify(songBlockService, never()).moveUp(anyInt());
    }

    @Test
    void nonWriterCannotMoveDown() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.moveDown(BLOCK_ID, model(), principal));
        verify(songBlockService, never()).moveDown(anyInt());
    }

    @Test
    void nonWriterCannotMoveTo() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.moveTo(BLOCK_ID, 2, model(), principal));
        verify(songBlockService, never()).moveTo(anyInt(), anyInt());
    }

    @Test
    void nonWriterCannotUndo() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.undo(DOCUMENT_ID, model(), principal));
        verify(songUndoRedoService, never()).undo(anyInt());
    }

    @Test
    void nonWriterCannotRedo() {
        givenNonWriterMember();

        assertEquals(FORBIDDEN, controller.redo(DOCUMENT_ID, model(), principal));
        verify(songUndoRedoService, never()).redo(anyInt());
    }

    /** Read-only: a member may poll the Edit menu's state without write access. */
    @Test
    void nonWriterCanReadUndoRedoStatus() {
        givenNonWriterMember();
        when(songUndoRedoService.canUndo(DOCUMENT_ID)).thenReturn(true);
        when(songUndoRedoService.canRedo(DOCUMENT_ID)).thenReturn(false);

        var response = controller.undoRedoStatus(DOCUMENT_ID, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("canUndo"));
        assertEquals(false, response.getBody().get("canRedo"));
    }

    @Test
    void nonMemberCannotReadUndoRedoStatus() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN,
                controller.undoRedoStatus(DOCUMENT_ID, principal).getStatusCode());
    }

    @Test
    void writerCanEditContent() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        assertEquals(HttpStatus.NO_CONTENT,
                controller.edit(BLOCK_ID, "new lyrics", principal).getStatusCode());
        verify(songBlockService).editContent(BLOCK_ID, "new lyrics");
    }

    /** An unknown block resolves to no project, so it is rejected before any write. */
    @Test
    void unknownBlockIsRejected() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(null);

        assertEquals(HttpStatus.FORBIDDEN,
                controller.edit(BLOCK_ID, "new lyrics", principal).getStatusCode());
        verify(songBlockService, never()).editContent(anyInt(), any());
    }
}
