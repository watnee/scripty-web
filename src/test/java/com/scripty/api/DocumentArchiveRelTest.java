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
 * Archiving is a write, so it is offered to an editor only — but unlike the
 * bulk delete and bulk email beside it, it carries no has-a-song condition:
 * notes archive exactly as songs do.
 */
class DocumentArchiveRelTest {

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
    void anEditorIsOfferedTheArchiveAndTheBulkArchive() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        var collection = collection(document(1, TextDocument.TYPE_SONG));
        assertTrue(collection.getLink(ApiRel.ARCHIVED).isPresent());
        assertTrue(collection.getLink(ApiRel.BULK_ARCHIVE).isPresent());
    }

    @Test
    void aReaderIsNotOfferedEither() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(false);

        var collection = collection(document(1, TextDocument.TYPE_SONG));
        assertFalse(collection.getLink(ApiRel.ARCHIVED).isPresent());
        assertFalse(collection.getLink(ApiRel.BULK_ARCHIVE).isPresent());
    }

    @Test
    void aProjectOfNotesAloneIsStillOfferedBoth() {
        // The point of departure from bulkDelete/bulkShareEmail, which both
        // require a song because their services skip anything that is not one.
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        var collection = collection(document(3, TextDocument.TYPE_NOTES));
        assertTrue(collection.getLink(ApiRel.ARCHIVED).isPresent());
        assertTrue(collection.getLink(ApiRel.BULK_ARCHIVE).isPresent());
        assertFalse(collection.getLink(ApiRel.BULK_DELETE).isPresent());
    }

    @Test
    void anEmptyProjectStillAdvertisesTheArchive() {
        // A client needs somewhere to send the first document; and a project can
        // have an empty list precisely because everything in it is archived.
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        assertTrue(collection().getLink(ApiRel.ARCHIVED).isPresent());
    }

    @Test
    void everyEditableDocumentCarriesItsOwnArchiveLink() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        var items = collection(document(1, TextDocument.TYPE_SONG),
                               document(2, TextDocument.TYPE_NOTES))
                .getContent().stream().toList();
        assertTrue(items.get(0).getLink(ApiRel.ARCHIVE).isPresent(), "song");
        assertTrue(items.get(1).getLink(ApiRel.ARCHIVE).isPresent(), "note");
    }

    @Test
    void aReadersDocumentsCarryNoArchiveLink() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(false);

        var items = collection(document(1, TextDocument.TYPE_SONG)).getContent().stream().toList();
        assertFalse(items.get(0).getLink(ApiRel.ARCHIVE).isPresent());
    }
}
