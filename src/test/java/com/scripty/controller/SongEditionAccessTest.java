package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Song versions may be switched by any project member, but creating, renaming,
 * deleting, and publishing require script write access — the same gate the song
 * editor uses, mirroring {@link SongVersionAccessTest}. These tests pin the
 * server-side half so a read-only UI cannot be bypassed by POSTing directly.
 */
class SongEditionAccessTest {

    private static final Integer DOCUMENT_ID = 7;
    private static final Integer EDITION_ID = 100;
    private static final Integer PROJECT_ID = 3;
    private static final String LIST = "redirect:/project/list";

    private final SongEditionController controller = new SongEditionController();
    private final SongEditionService songEditionService = mock(SongEditionService.class);
    private final SongBlockService songBlockService = mock(SongBlockService.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final Principal principal = () -> "member";

    @BeforeEach
    void setUp() {
        controller.songEditionService = songEditionService;
        controller.songBlockService = songBlockService;
        controller.projectAccess = projectAccess;

        when(songBlockService.projectIdForDocument(DOCUMENT_ID)).thenReturn(PROJECT_ID);
    }

    private String editUrl(Integer editionId) {
        return "redirect:/project/documents/edit?id=" + DOCUMENT_ID + "&editionId=" + editionId;
    }

    @Test
    void readOnlyMemberCannotMutateVersions() {
        // canEditScript defaults to false: a member without write access.
        assertEquals(LIST, controller.create(DOCUMENT_ID, "Blue", null, principal));
        assertEquals(LIST, controller.rename(DOCUMENT_ID, EDITION_ID, "New name", principal));
        assertEquals(LIST, controller.delete(DOCUMENT_ID, EDITION_ID, principal));
        assertEquals(LIST, controller.setDefault(DOCUMENT_ID, EDITION_ID, principal));
        assertEquals(LIST, controller.setPublished(DOCUMENT_ID, EDITION_ID, principal));

        verify(songEditionService, never()).createEdition(any(), any(), any());
        verify(songEditionService, never()).renameEdition(any(), any(), any());
        verify(songEditionService, never()).deleteEdition(any(), any());
        verify(songEditionService, never()).setDefaultEdition(any(), any());
        verify(songEditionService, never()).setPublishedEdition(any(), any());
    }

    @Test
    void writerDelegatesEveryMutationToTheService() {
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(songEditionService.createEdition(DOCUMENT_ID, "Blue", null)).thenReturn(edition(EDITION_ID));
        when(songEditionService.getDefaultForDocument(DOCUMENT_ID)).thenReturn(edition(EDITION_ID));

        assertEquals(editUrl(EDITION_ID), controller.create(DOCUMENT_ID, "Blue", null, principal));
        assertEquals(editUrl(EDITION_ID), controller.rename(DOCUMENT_ID, EDITION_ID, "New name", principal));
        assertEquals(editUrl(EDITION_ID), controller.delete(DOCUMENT_ID, EDITION_ID, principal));
        assertEquals(editUrl(EDITION_ID), controller.setDefault(DOCUMENT_ID, EDITION_ID, principal));
        assertEquals(editUrl(EDITION_ID), controller.setPublished(DOCUMENT_ID, EDITION_ID, principal));

        verify(songEditionService).createEdition(DOCUMENT_ID, "Blue", null);
        verify(songEditionService).renameEdition(EDITION_ID, DOCUMENT_ID, "New name");
        verify(songEditionService).deleteEdition(EDITION_ID, DOCUMENT_ID);
        verify(songEditionService).setDefaultEdition(EDITION_ID, DOCUMENT_ID);
        verify(songEditionService).setPublishedEdition(EDITION_ID, DOCUMENT_ID);
    }

    @Test
    void switchRequiresProjectAccess() {
        // canAccessProject defaults to false: not even a member.
        assertEquals(LIST, controller.switchEdition(DOCUMENT_ID, EDITION_ID, principal));

        verify(songEditionService, never()).resolveForAccess(any(), any(), anyBoolean());
    }

    @Test
    void memberMaySwitchVersions() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        // A viewer (no write access) resolves without the browse privilege.
        when(songEditionService.resolveForAccess(DOCUMENT_ID, EDITION_ID, false)).thenReturn(edition(EDITION_ID));

        assertEquals(editUrl(EDITION_ID), controller.switchEdition(DOCUMENT_ID, EDITION_ID, principal));
    }

    private static SongEdition edition(Integer id) {
        SongEdition e = new SongEdition();
        e.setId(id);
        return e;
    }
}
