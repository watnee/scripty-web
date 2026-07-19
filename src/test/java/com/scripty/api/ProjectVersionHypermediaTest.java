package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reading a project's version history only needs project access, but restore,
 * delete and create need edit permission — so a read-only viewer must not be
 * advertised links that would answer 403. The song counterpart is
 * {@link SongHypermediaTest}; this pins the same rule for project versions,
 * which previously emitted the mutation links unconditionally.
 */
class ProjectVersionHypermediaTest {

    private static final int PROJECT_ID = 3;
    private static final int VERSION_ID = 11;

    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final ProjectVersionResourceAssembler assembler = new ProjectVersionResourceAssembler();

    @BeforeEach
    void setUp() {
        assembler.projectAccess = projectAccess;

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

    private VersionHistoryViewModel versionHistory() {
        VersionViewModel version = new VersionViewModel();
        version.setId(VERSION_ID);
        version.setLabel("Draft");
        VersionHistoryViewModel history = new VersionHistoryViewModel();
        history.setProjectId(PROJECT_ID);
        history.setVersions(List.of(version));
        return history;
    }

    @Test
    void editorSeesRestoreAndDeleteOnAVersion() {
        givenCanEdit(true);

        EntityModel<ProjectVersionResource> model =
                assembler.toModel(versionHistory().getVersions().get(0), PROJECT_ID, null);

        assertTrue(model.getLink(ApiRel.RESTORE).isPresent());
        assertTrue(model.getLink(ApiRel.DELETE).isPresent());
        // Self always carries an implicit "default" affordance for its own GET;
        // restore and delete are the two this assembler adds on top.
        assertEquals(3, model.getRequiredLink(IanaLinkRelations.SELF).getAffordances().size(),
                "an editor's self link should carry the restore and delete affordances");
    }

    @Test
    void readOnlyViewerSeesNeitherRestoreNorDelete() {
        givenCanEdit(false);

        EntityModel<ProjectVersionResource> model =
                assembler.toModel(versionHistory().getVersions().get(0), PROJECT_ID, null);

        assertFalse(model.getLink(ApiRel.RESTORE).isPresent());
        assertFalse(model.getLink(ApiRel.DELETE).isPresent());
        assertEquals(1, model.getRequiredLink(IanaLinkRelations.SELF).getAffordances().size(),
                "a read-only viewer should keep only the implicit self affordance");
        // Navigation stays available — only the mutations are withheld.
        assertTrue(model.getLink(ApiRel.VERSIONS).isPresent());
        assertTrue(model.getLink(ApiRel.PROJECT).isPresent());
    }

    @Test
    void editorSeesCreateOnTheCollection() {
        givenCanEdit(true);

        CollectionModel<EntityModel<ProjectVersionResource>> collection =
                assembler.toCollection(versionHistory(), null);

        assertTrue(collection.getLink(ApiRel.CREATE).isPresent());
    }

    @Test
    void readOnlyViewerSeesNoCreateOnTheCollection() {
        givenCanEdit(false);

        CollectionModel<EntityModel<ProjectVersionResource>> collection =
                assembler.toCollection(versionHistory(), null);

        assertFalse(collection.getLink(ApiRel.CREATE).isPresent());
        assertTrue(collection.getLink(ApiRel.PROJECT).isPresent());
    }
}
