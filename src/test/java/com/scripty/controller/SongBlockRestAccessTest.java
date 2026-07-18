package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.scripty.api.CreateSongBlockBelowRequest;
import com.scripty.api.EditSongBlockRequest;
import com.scripty.api.MoveBlockRequest;
import com.scripty.api.SetSongBlockHighlightRequest;
import com.scripty.api.SongBlockResourceAssembler;
import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The REST song editor is a second door onto the same lyrics, so it has to
 * enforce the same rule as {@link SongBlockAccessTest} pins for the HTMX one:
 * any project member may read, only a writer may change anything.
 */
class SongBlockRestAccessTest {

    private static final Integer BLOCK_ID = 7;
    private static final Integer DOCUMENT_ID = 3;
    private static final Integer EDITION_ID = 100;
    private static final Integer PROJECT_ID = 42;

    private final SongBlockRestController controller = new SongBlockRestController();
    private final SongBlockService songBlockService = mock(SongBlockService.class);
    private final SongEditionService songEditionService = mock(SongEditionService.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final SongUndoRedoService songUndoRedoService = mock(SongUndoRedoService.class);
    private final SongVersionService songVersionService = mock(SongVersionService.class);
    private final Principal principal = () -> "member";

    @BeforeEach
    void setUp() {
        controller.songBlockService = songBlockService;
        controller.songEditionService = songEditionService;
        controller.projectAccess = projectAccess;
        controller.songUndoRedoService = songUndoRedoService;
        controller.songVersionService = songVersionService;
        SongBlockResourceAssembler assembler = new SongBlockResourceAssembler();
        ReflectionTestUtils.setField(assembler, "projectAccess", projectAccess);
        controller.assembler = assembler;

        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(PROJECT_ID);
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);

        // The edition resolution is beside the point for the permission gate, but the
        // permitted paths resolve one, so return a simple version to avoid NPEs.
        SongEdition edition = new SongEdition();
        edition.setId(EDITION_ID);
        when(songEditionService.requireForDocument(anyInt(), any())).thenReturn(edition);
        when(songEditionService.ensureDefaultEdition(anyInt())).thenReturn(edition);
    }

    /** A project member who is not a writer: access yes, script edit no. */
    private void givenNonWriterMember() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(false);
    }

    @Test
    void nonWriterCannotUpdate() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN,
                controller.update(BLOCK_ID, new EditSongBlockRequest("new lyrics"), principal).getStatusCode());
        verify(songBlockService, never()).editContent(anyInt(), any());
        verifyNoInteractions(songUndoRedoService, songVersionService);
    }

    @Test
    void nonWriterCannotCreateBelow() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN,
                controller.createBelow(BLOCK_ID, new CreateSongBlockBelowRequest("text"), principal).getStatusCode());
        verify(songBlockService, never()).createBelow(anyInt(), any());
    }

    @Test
    void nonWriterCannotAppend() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN, controller.append(DOCUMENT_ID, EDITION_ID, principal).getStatusCode());
        verify(songBlockService, never()).appendBlock(anyInt(), any());
    }

    @Test
    void nonWriterCannotDelete() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN, controller.delete(BLOCK_ID, principal).getStatusCode());
        verify(songBlockService, never()).deleteBlock(anyInt());
    }

    @Test
    void nonWriterCannotSetHighlight() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN,
                controller.setHighlight(BLOCK_ID, new SetSongBlockHighlightRequest("yellow"), principal)
                        .getStatusCode());
        verify(songBlockService, never()).setHighlight(anyInt(), any());
    }

    @Test
    void nonWriterCannotMove() {
        givenNonWriterMember();

        assertEquals(HttpStatus.FORBIDDEN,
                controller.move(BLOCK_ID, new MoveBlockRequest(2), principal).getStatusCode());
        verify(songBlockService, never()).moveTo(anyInt(), anyInt());
    }

    /** Read-only: a member may still read the lyrics. */
    @Test
    void nonWriterCanList() {
        givenNonWriterMember();

        assertEquals(HttpStatus.OK, controller.list(DOCUMENT_ID, EDITION_ID, principal).getStatusCode());
    }

    @Test
    void nonMemberCannotList() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, controller.list(DOCUMENT_ID, EDITION_ID, principal).getStatusCode());
    }

    @Test
    void nonMemberCannotShow() {
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, controller.show(BLOCK_ID, principal).getStatusCode());
        verify(songBlockService, never()).read(anyInt());
    }

    /** An unknown block resolves to no project, so it is rejected before any write. */
    @Test
    void unknownBlockIsRejected() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(songBlockService.projectIdForBlock(BLOCK_ID)).thenReturn(null);

        assertEquals(HttpStatus.FORBIDDEN,
                controller.update(BLOCK_ID, new EditSongBlockRequest("new lyrics"), principal).getStatusCode());
        verify(songBlockService, never()).editContent(anyInt(), any());
    }

    /** An unknown document resolves to no project, so append is rejected too. */
    @Test
    void unknownDocumentIsRejected() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(null);

        assertEquals(HttpStatus.FORBIDDEN, controller.append(DOCUMENT_ID, EDITION_ID, principal).getStatusCode());
        verify(songBlockService, never()).appendBlock(anyInt(), any());
    }
}
