package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import com.scripty.viewmodel.song.versionhistory.SongVersionViewModel;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The song API tells a client what it may do by which links it emits: a
 * read-only member gets no restore, delete or update affordance. These tests
 * pin that, since the links are what the UI gates on — the controllers enforce
 * the same rule independently.
 */
class SongHypermediaTest {

    private static final int DOCUMENT_ID = 7;
    private static final int PROJECT_ID = 3;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final SongVersionResourceAssembler versionAssembler = new SongVersionResourceAssembler();
    private final SongBlockResourceAssembler blockAssembler = new SongBlockResourceAssembler();

    @BeforeEach
    void setUp() {
        versionAssembler.projectAccess = projectAccess;
        blockAssembler.projectAccess = projectAccess;

        // linkTo(methodOn(...)) needs a current request to build absolute hrefs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken("member", "n/a", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(projectAccess.currentUser(any(Authentication.class))).thenReturn(new User());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void givenCanEdit(boolean canEdit) {
        when(projectAccess.canEditScriptForCurrentUser(PROJECT_ID)).thenReturn(canEdit);
    }

    private SongVersionHistoryViewModel versionHistory() {
        SongVersionViewModel version = new SongVersionViewModel();
        version.setId(11);
        version.setLabel("Draft");
        SongVersionHistoryViewModel history = new SongVersionHistoryViewModel();
        history.setDocumentId(DOCUMENT_ID);
        history.setProjectId(PROJECT_ID);
        history.setVersions(List.of(version));
        return history;
    }

    private List<SongBlockViewModel> blocks() {
        return List.of(new SongBlockViewModel(5, DOCUMENT_ID, 0, "line one", null));
    }

    private static boolean hasLink(RepresentationModel<?> model, String rel) {
        return model.getLink(rel).isPresent();
    }

    @Test
    void writerSeesVersionMutationLinks() {
        givenCanEdit(true);

        CollectionModel<EntityModel<SongVersionResource>> collection =
                versionAssembler.toCollection(versionHistory());
        EntityModel<SongVersionResource> version = collection.getContent().iterator().next();

        assertTrue(hasLink(collection, ApiRel.CREATE));
        assertTrue(hasLink(version, ApiRel.RESTORE));
        assertTrue(hasLink(version, ApiRel.DELETE));
    }

    @Test
    void readOnlyMemberSeesNoVersionMutationLinks() {
        givenCanEdit(false);

        CollectionModel<EntityModel<SongVersionResource>> collection =
                versionAssembler.toCollection(versionHistory());
        EntityModel<SongVersionResource> version = collection.getContent().iterator().next();

        assertFalse(hasLink(collection, ApiRel.CREATE));
        assertFalse(hasLink(version, ApiRel.RESTORE));
        assertFalse(hasLink(version, ApiRel.DELETE));
        // Reading and navigating stay available.
        assertTrue(version.getLink(IanaLinkRelations.SELF).isPresent());
        assertTrue(hasLink(version, ApiRel.SONG));
        assertTrue(hasLink(collection, ApiRel.SONG_BLOCKS));
    }

    @Test
    void writerSeesBlockMutationLinks() {
        givenCanEdit(true);

        CollectionModel<EntityModel<SongBlockResource>> collection =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);
        EntityModel<SongBlockResource> block = collection.getContent().iterator().next();

        assertTrue(hasLink(collection, ApiRel.CREATE));
        assertTrue(hasLink(block, ApiRel.UPDATE));
        assertTrue(hasLink(block, ApiRel.DELETE));
        assertTrue(hasLink(block, ApiRel.CREATE_BELOW));
        assertTrue(hasLink(block, ApiRel.MOVE));
        assertTrue(hasLink(block, ApiRel.SET_HIGHLIGHT));
    }

    @Test
    void readOnlyMemberSeesNoBlockMutationLinks() {
        givenCanEdit(false);

        CollectionModel<EntityModel<SongBlockResource>> collection =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);
        EntityModel<SongBlockResource> block = collection.getContent().iterator().next();

        assertFalse(hasLink(collection, ApiRel.CREATE));
        assertFalse(hasLink(block, ApiRel.UPDATE));
        assertFalse(hasLink(block, ApiRel.DELETE));
        assertFalse(hasLink(block, ApiRel.CREATE_BELOW));
        assertFalse(hasLink(block, ApiRel.MOVE));
        assertFalse(hasLink(block, ApiRel.SET_HIGHLIGHT));
        // Reading and navigating stay available.
        assertTrue(hasLink(block, ApiRel.SONG));
        assertTrue(hasLink(block, ApiRel.VERSIONS));
        assertTrue(hasLink(block, ApiRel.PROJECT));
    }

    /**
     * Undo walks back the caller's own checkpoints, so it belongs to whoever
     * can type. The trash does not: seeing what was cut is reading, and the web
     * recovery page opens for a reader too.
     */
    @Test
    void writerSeesUndoRedoAndTrash() {
        givenCanEdit(true);

        CollectionModel<EntityModel<SongBlockResource>> collection =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);

        assertTrue(hasLink(collection, ApiRel.UNDO_REDO_STATUS));
        assertTrue(hasLink(collection, ApiRel.TRASH));
    }

    @Test
    void readOnlyMemberSeesTheTrashButNoUndo() {
        givenCanEdit(false);

        CollectionModel<EntityModel<SongBlockResource>> collection =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);

        assertFalse(hasLink(collection, ApiRel.UNDO_REDO_STATUS),
                "a reader has no checkpoints of their own to step back through");
        assertTrue(hasLink(collection, ApiRel.TRASH),
                "reading what was cut from a song needs only access to the song");
    }

    /**
     * A method-derived self link always carries one implicit affordance (its own
     * GET), so gating is measured by the writer's self link exposing strictly
     * more — the update/delete (item) and append (collection) HAL-FORMS
     * templates that a read-only member never sees.
     */
    @Test
    void writerSelfLinksExposeMoreAffordancesThanReadOnly() {
        givenCanEdit(false);
        CollectionModel<EntityModel<SongBlockResource>> readOnly =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);
        int readOnlyBlockAffordances = readOnly.getContent().iterator().next()
                .getRequiredLink(IanaLinkRelations.SELF).getAffordances().size();
        int readOnlyCollectionAffordances =
                readOnly.getRequiredLink(IanaLinkRelations.SELF).getAffordances().size();

        givenCanEdit(true);
        CollectionModel<EntityModel<SongBlockResource>> writer =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);
        int writerBlockAffordances = writer.getContent().iterator().next()
                .getRequiredLink(IanaLinkRelations.SELF).getAffordances().size();
        int writerCollectionAffordances =
                writer.getRequiredLink(IanaLinkRelations.SELF).getAffordances().size();

        assertTrue(writerBlockAffordances > readOnlyBlockAffordances);
        assertTrue(writerCollectionAffordances > readOnlyCollectionAffordances);
    }

    @Test
    void anonymousClientSeesNoMutationLinks() {
        SecurityContextHolder.clearContext();

        CollectionModel<EntityModel<SongBlockResource>> collection =
                blockAssembler.toCollection(blocks(), DOCUMENT_ID, PROJECT_ID);
        EntityModel<SongBlockResource> block = collection.getContent().iterator().next();

        assertFalse(hasLink(collection, ApiRel.CREATE));
        assertFalse(hasLink(block, ApiRel.UPDATE));
    }
}
