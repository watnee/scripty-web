package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.SongVersionResourceAssembler;
import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongVersionService;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;

/**
 * Song snapshots are readable by any project member but only writable with
 * script edit permission, matching the screenplay and the song editor (which
 * renders read-only without it).
 */
class SongVersionAccessTest {

    private static final int DOCUMENT_ID = 7;
    private static final int EDITION_ID = 100;
    private static final int PROJECT_ID = 3;
    private static final int VERSION_ID = 11;

    private final SongVersionController controller = new SongVersionController();
    private final SongVersionRestController restController = new SongVersionRestController();
    private final SongVersionService songVersionService = mock(SongVersionService.class);
    private final SongBlockService songBlockService = mock(SongBlockService.class);
    private final SongEditionService songEditionService = mock(SongEditionService.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final Principal principal = () -> "reader";

    @BeforeEach
    void setUp() {
        controller.songVersionService = songVersionService;
        controller.songBlockService = songBlockService;
        controller.projectAccess = projectAccess;

        restController.songVersionService = songVersionService;
        restController.songBlockService = songBlockService;
        restController.songEditionService = songEditionService;
        restController.projectAccess = projectAccess;
        restController.assembler = new SongVersionResourceAssembler();

        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
        when(songVersionService.getVersionHistoryViewModel(eq(DOCUMENT_ID), any()))
                .thenReturn(new SongVersionHistoryViewModel());

        SongEdition edition = new SongEdition();
        edition.setId(EDITION_ID);
        when(songEditionService.requireForDocument(any(), any())).thenReturn(edition);
        when(songEditionService.ensureDefaultEdition(any())).thenReturn(edition);
    }

    /** Member of the project, but not a writer: may look, may not touch. */
    private void givenReadOnlyMember() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);
    }

    @Test
    void readOnlyMemberSeesHistoryWithoutEditAffordances() {
        givenReadOnlyMember();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(DOCUMENT_ID, EDITION_ID, model, principal);

        assertEquals("song/versionHistory", view);
        assertEquals(false, model.get("canEditScript"));
    }

    @Test
    void writerSeesHistoryWithEditAffordances() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(DOCUMENT_ID, EDITION_ID, model, principal);

        assertEquals("song/versionHistory", view);
        assertEquals(true, model.get("canEditScript"));
    }

    @Test
    void readOnlyMemberCannotCreateRestoreOrDelete() {
        givenReadOnlyMember();

        assertEquals("redirect:/project/list", controller.create(DOCUMENT_ID, EDITION_ID, "Draft", principal));
        assertEquals("redirect:/project/list",
                controller.restore(VERSION_ID, DOCUMENT_ID, EDITION_ID, principal));
        assertEquals("redirect:/project/list",
                controller.delete(VERSION_ID, DOCUMENT_ID, EDITION_ID, principal));

        verify(songVersionService, never()).createVersion(any(), any(), any());
        verify(songVersionService, never()).restoreVersionForDocument(any(), any());
        verify(songVersionService, never()).deleteVersionForDocument(any(), any());
    }

    @Test
    void restReadOnlyMemberMayListButNotMutate() {
        givenReadOnlyMember();

        assertEquals(HttpStatus.OK, restController.list(DOCUMENT_ID, EDITION_ID, principal).getStatusCode());

        assertEquals(HttpStatus.FORBIDDEN,
                restController.create(DOCUMENT_ID, EDITION_ID, null, principal).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                restController.restore(VERSION_ID, DOCUMENT_ID, EDITION_ID, principal).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                restController.delete(VERSION_ID, DOCUMENT_ID, EDITION_ID, principal).getStatusCode());

        verify(songVersionService, never()).createVersion(any(), any(), any());
        verify(songVersionService, never()).restoreVersionForDocument(any(), any());
        verify(songVersionService, never()).deleteVersionForDocument(any(), any());
    }

    @Test
    void restNonMemberIsForbiddenEvenForReads() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);

        ResponseEntity<?> response = restController.list(DOCUMENT_ID, EDITION_ID, principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("redirect:/project/list",
                controller.list(DOCUMENT_ID, EDITION_ID, new ExtendedModelMap(), principal));
    }

    /** A song with no owning project must never resolve to an accessible document. */
    @Test
    void unknownDocumentIsForbidden() {
        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(null);

        assertEquals(HttpStatus.FORBIDDEN, restController.list(DOCUMENT_ID, EDITION_ID, principal).getStatusCode());
        assertEquals("redirect:/project/list", controller.create(DOCUMENT_ID, EDITION_ID, "Draft", principal));
    }
}
