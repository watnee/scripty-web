package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.config.FeatureFlags;
import com.scripty.dto.TextDocument;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
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
 * The two "take the whole set away" exports the web menus have always offered:
 * a songbook of a project's songs, and every project as one archive. Both are
 * reads, so they are offered to a view-only collaborator too; both are pointless
 * when the collection is empty, so neither is advertised then.
 */
class BundleExportRelsTest {

    private static final int PROJECT_ID = 7;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final TextDocumentResourceAssembler documents = new TextDocumentResourceAssembler();
    private final ProjectResourceAssembler projects = new ProjectResourceAssembler();

    @BeforeEach
    void setUp() {
        documents.projectAccess = projectAccess;
        projects.projectAccess = projectAccess;
        projects.featureFlags = mock(FeatureFlags.class);
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

    private static ProjectViewModel project(int id) {
        ProjectViewModel project = new ProjectViewModel();
        project.setId(id);
        project.setTitle("Project " + id);
        return project;
    }

    private static boolean hasRel(CollectionModel<?> collection, String rel) {
        return collection.getLink(rel).isPresent();
    }

    @Test
    void songbookExportsRideOnTheDocumentCollection() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(false);

        CollectionModel<EntityModel<TextDocumentResource>> collection = documents.toCollection(
                List.of(document(1, TextDocument.TYPE_SONG), document(2, TextDocument.TYPE_NOTES)),
                PROJECT_ID, null);

        // A reader gets the exports even though the edit-gated rels are absent.
        assertTrue(hasRel(collection, ApiRel.EXPORT_SONGS_TXT));
        assertTrue(hasRel(collection, ApiRel.EXPORT_SONGS_PDF));
        assertTrue(hasRel(collection, ApiRel.EXPORT_SONGS_DOCX));
        assertTrue(hasRel(collection, ApiRel.EXPORT_SONGS_EPUB));
        assertTrue(hasRel(collection, ApiRel.EXPORT_SONGS_MUSICXML));
        assertFalse(hasRel(collection, ApiRel.IMPORT_DOCUMENT));
    }

    @Test
    void aProjectWithoutSongsHasNoSongbook() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(true);

        CollectionModel<EntityModel<TextDocumentResource>> collection = documents.toCollection(
                List.of(document(3, TextDocument.TYPE_NOTES)), PROJECT_ID, TextDocument.TYPE_NOTES);

        assertFalse(hasRel(collection, ApiRel.EXPORT_SONGS_TXT));
        assertFalse(hasRel(collection, ApiRel.EXPORT_SONGS_PDF));
        assertFalse(hasRel(collection, ApiRel.EXPORT_SONGS_DOCX));
        assertFalse(hasRel(collection, ApiRel.EXPORT_SONGS_EPUB));
        assertFalse(hasRel(collection, ApiRel.EXPORT_SONGS_MUSICXML));
    }

    @Test
    void aSongOffersItselfAsAScoreAndANoteDoesNot() {
        when(projectAccess.canEditScriptForCurrentUser(any())).thenReturn(false);

        EntityModel<TextDocumentResource> song = documents.toModel(document(1, TextDocument.TYPE_SONG));
        EntityModel<TextDocumentResource> note = documents.toModel(document(2, TextDocument.TYPE_NOTES));

        assertTrue(song.getLink(ApiRel.EXPORT_SONG_MUSICXML).isPresent());
        assertFalse(note.getLink(ApiRel.EXPORT_SONG_MUSICXML).isPresent());
    }

    @Test
    void theProjectCollectionOffersItselfAsOneArchive() {
        CollectionModel<EntityModel<ProjectResource>> collection =
                projects.toProjectCollection(List.of(project(1), project(2)));

        assertTrue(hasRel(collection, ApiRel.EXPORT_PROJECTS));
    }

    @Test
    void anEmptyProjectListHasNothingToBundle() {
        CollectionModel<EntityModel<ProjectResource>> collection =
                projects.toProjectCollection(List.of());

        assertFalse(hasRel(collection, ApiRel.EXPORT_PROJECTS));
        // Importing is still the way out of an empty list.
        assertTrue(hasRel(collection, ApiRel.IMPORT_PROJECT));
    }
}
