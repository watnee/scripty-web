package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.dto.Project;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectService;
import com.scripty.viewmodel.project.projectprofile.ProjectShareUserViewModel;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who can already see a project, asserted through the real HAL serializer.
 *
 * <p>The embed key is the contract here: the collection relation on
 * {@link ProjectAccessUserResource} is what puts the people under
 * {@code _embedded.access}, and it is namespaced by the curie provider on the
 * way out. Get that wrong and the client decodes an empty list — which reads as
 * "nobody else is in here" rather than as an error.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectAccessListJsonTest {

    private static final int PROJECT_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private ProjectService projectService;

    private ProjectShareUserViewModel person(String name, String access, boolean canEdit) {
        ProjectShareUserViewModel viewModel = new ProjectShareUserViewModel();
        viewModel.setDisplayName(name);
        viewModel.setAccessLabel(access);
        viewModel.setCanEdit(canEdit);
        viewModel.setPermissionLabel(canEdit ? "Can edit" : "View only");
        return viewModel;
    }

    private void givenAccess() {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(projectService.read(PROJECT_ID)).thenReturn(new Project());
    }

    @Test
    void peopleAreEmbeddedUnderTheAccessRelation() throws Exception {
        givenAccess();
        when(projectService.getProjectShareAccessUsers(PROJECT_ID)).thenReturn(List.of(
                person("Ada Lovelace", "Writer", true),
                person("Grace Hopper", "On the Lighting team", false)));

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/access")
                        .accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.['scripty:access'][0].displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$._embedded.['scripty:access'][0].canEdit").value(true))
                .andExpect(jsonPath("$._embedded.['scripty:access'][0].permissionLabel").value("Can edit"))
                // The reason someone is here is rendered on the server, so the
                // client never has to restate the access rules itself.
                .andExpect(jsonPath("$._embedded.['scripty:access'][1].accessLabel")
                        .value("On the Lighting team"))
                .andExpect(jsonPath("$._links.['scripty:project'].href").exists());
    }

    /**
     * A project only one person can see still answers, with that one person.
     * An empty {@code _embedded} would be indistinguishable from a failure.
     */
    @Test
    void aProjectWithOneReaderStillListsThem() throws Exception {
        givenAccess();
        when(projectService.getProjectShareAccessUsers(PROJECT_ID))
                .thenReturn(List.of(person("Ada Lovelace", "Writer", true)));

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/access")
                        .accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.['scripty:access'][1]").doesNotExist())
                .andExpect(jsonPath("$._embedded.['scripty:access'][0].displayName").value("Ada Lovelace"));
    }

    @Test
    void someoneWithoutAccessCannotSeeWhoElseHasIt() throws Exception {
        when(projectAccess.canAccessProject(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/access")
                        .accept(MediaTypes.HAL_JSON).with(user("stranger").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
