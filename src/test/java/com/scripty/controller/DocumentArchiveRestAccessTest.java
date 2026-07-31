package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.ApiRel;
import com.scripty.api.ArchivedDocumentResource;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.TextDocumentService;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The archive is editor-only end to end: unlike the trash, which lets any
 * member read what was cut, there is nothing for a reader to do here — an
 * archived document is simply out of their list, and the way back is a write.
 */
class DocumentArchiveRestAccessTest {

    private static final Integer PROJECT_ID = 42;
    private static final Integer SONG_ID = 7;
    private static final Integer NOTE_ID = 8;

    private final DocumentArchiveRestController controller = new DocumentArchiveRestController();
    private final TextDocumentService textDocumentService = mock(TextDocumentService.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final Principal principal = () -> "member";
    private final User user = new User();

    @BeforeEach
    void setUp() {
        controller.textDocumentService = textDocumentService;
        controller.projectAccess = projectAccess;
        user.setId(3);
        when(projectAccess.currentUser(any())).thenReturn(user);
        when(textDocumentService.getArchiveViewModel(PROJECT_ID, user)).thenReturn(archive());
        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private TextDocumentListViewModel archive() {
        TextDocumentListViewModel viewModel = new TextDocumentListViewModel();
        viewModel.setProjectId(PROJECT_ID);
        viewModel.setProjectTitle("The Big Musical");
        viewModel.setRetentionUnlimited(true);
        viewModel.setSongs(List.of(document(SONG_ID, TextDocument.TYPE_SONG, "Opening Number")));
        viewModel.setDrafts(List.of(document(NOTE_ID, TextDocument.TYPE_NOTES, "Scene ideas")));
        return viewModel;
    }

    private TextDocumentViewModel document(Integer id, String type, String title) {
        TextDocumentViewModel document = new TextDocumentViewModel();
        document.setId(id);
        document.setProjectId(PROJECT_ID);
        document.setTitle(title);
        document.setDocumentType(type);
        document.setDocumentTypeLabel(TextDocument.typeLabelFor(type));
        document.setArchivedAt(LocalDateTime.now().minusDays(9));
        return document;
    }

    @SuppressWarnings("unchecked")
    private CollectionModel<EntityModel<ArchivedDocumentResource>> body(ResponseEntity<?> response) {
        return (CollectionModel<EntityModel<ArchivedDocumentResource>>) response.getBody();
    }

    @Test
    void aReaderCannotSeeTheArchive() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN, controller.list(PROJECT_ID, principal).getStatusCode());
        verify(textDocumentService, never()).getArchiveViewModel(anyInt(), any());
    }

    @Test
    void aReaderCannotUnarchive() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(false);

        assertEquals(HttpStatus.FORBIDDEN,
                controller.unarchive(SONG_ID, PROJECT_ID, principal).getStatusCode());
        verify(textDocumentService, never()).unarchive(anyInt(), anyInt(), any());
    }

    @Test
    void anEditorSeesSongsAndNotesInOneArchiveWithAWayBackAndAWayIn() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        ResponseEntity<?> response = controller.list(PROJECT_ID, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        var items = body(response).getContent().stream().toList();
        // The view model splits by type for two web tabs; over the API they are
        // one archive, told apart by documentType.
        assertEquals(2, items.size());
        assertEquals(TextDocument.TYPE_SONG, items.get(0).getContent().getDocumentType());
        assertEquals(TextDocument.TYPE_NOTES, items.get(1).getContent().getDocumentType());
        for (EntityModel<ArchivedDocumentResource> item : items) {
            assertTrue(item.getLink(ApiRel.UNARCHIVE).isPresent());
            // Unlike a trashed document, an archived one can still be opened.
            assertTrue(item.getLink(ApiRel.DOCUMENT).isPresent());
            assertTrue(item.getLink(ApiRel.ARCHIVED).isPresent());
        }
    }

    @Test
    void nothingExpiresSoNoPurgeDateIsEvenRepresentable() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);

        ArchivedDocumentResource song =
                body(controller.list(PROJECT_ID, principal)).getContent().iterator().next().getContent();
        assertTrue(song.getArchivedAt() != null, "when it was put aside is known");
        // The resource has no purge date at all — the field does not exist,
        // which is the point of keeping it apart from DeletedDocumentResource.
        assertNull(song.getLink(ApiRel.PURGE).orElse(null));
    }

    @Test
    void unarchivingSomethingNoLongerThereIsNotFound() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(textDocumentService.unarchive(SONG_ID, PROJECT_ID, user)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND,
                controller.unarchive(SONG_ID, PROJECT_ID, principal).getStatusCode());
    }

    @Test
    void unarchivingAnswersWithTheRefreshedArchive() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(textDocumentService.unarchive(SONG_ID, PROJECT_ID, user)).thenReturn(new TextDocument());

        ResponseEntity<?> response = controller.unarchive(SONG_ID, PROJECT_ID, principal);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(body(response).getLink(ApiRel.DOCUMENTS).isPresent(),
                "the caller's next stop is the list it came from");
    }
}
