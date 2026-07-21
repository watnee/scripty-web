package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.TextDocument;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Deleting several songs at once, the way the web list's checkbox column does.
 * Unlike the songbook exports it is a write, so it is offered to an editor
 * only; and like them it is pointless without a song, so a project of notes
 * alone is never offered it.
 */
class DocumentBulkDeleteRelTest {

    private static final int PROJECT_ID = 7;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final TextDocumentResourceAssembler documents = new TextDocumentResourceAssembler();

    @BeforeEach
    void setUp() {
        documents.projectAccess = projectAccess;
        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static TextDocumentViewModel document(int id, String type) {
        TextDocumentViewModel document = new TextDocumentViewModel();
        document.setId(id);
        document.setProjectId(PROJECT_ID);
        document.setTitle("Document " + id);
        document.setDocumentType(type);
        return document;
    }

    private CollectionModel<EntityModel<TextDocumentResource>> collection(TextDocumentViewModel... items) {
        return documents.toCollection(List.of(items), PROJECT_ID, null);
    }

    @Test
    void anEditorWithSongsIsOfferedTheBulkDelete() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        assertTrue(collection(document(1, TextDocument.TYPE_SONG),
                              document(2, TextDocument.TYPE_NOTES))
                .getLink(ApiRel.BULK_DELETE).isPresent());
    }

    @Test
    void aReaderIsNotOfferedIt() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(false);

        assertFalse(collection(document(1, TextDocument.TYPE_SONG))
                .getLink(ApiRel.BULK_DELETE).isPresent());
        // The reader still gets the songbook: exporting is a read.
        assertTrue(collection(document(1, TextDocument.TYPE_SONG))
                .getLink(ApiRel.EXPORT_SONGS_TXT).isPresent());
    }

    @Test
    void aProjectOfNotesAloneHasNothingToBulkDelete() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        assertFalse(collection(document(3, TextDocument.TYPE_NOTES))
                .getLink(ApiRel.BULK_DELETE).isPresent());
    }
}
