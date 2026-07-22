package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.dto.Project;
import com.scripty.dto.Team;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.InvitationService;
import com.scripty.service.ProjectService;
import com.scripty.service.ViewInvitationService;
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
 * The teams a collaborator can be invited into.
 *
 * <p>This exists because inviting an editor <em>requires</em> a team and there
 * was no way over the API to learn a valid one: {@code /api/team} is admin-only,
 * and the project resource carries team names rather than ids. A client with no
 * list can only send {@code teamId: null}, which the service rejects — so the
 * whole collaborator invitation path was unreachable.
 *
 * <p>Asserted through the real HAL serializer rather than on the model, because
 * the embed key and the curie prefix are the part a client actually reads. If
 * the collection relation were wrong the client would decode an empty list and
 * quietly show "this project has no teams", which looks like an answer.
 */
@SpringBootTest(properties = "app.features.api-invitations=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InviteTeamsApiTest {

    private static final int PROJECT_ID = 1;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private InvitationService invitationService;

    @MockBean
    private ViewInvitationService viewInvitationService;

    private Team team(Integer id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private void givenEditorOfProjectWithTeams(Team... teams) {
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        Project project = new Project();
        project.setTeams(List.of(teams));
        when(projectService.readWithTeams(PROJECT_ID)).thenReturn(project);
    }

    @Test
    void theProjectsTeamsAreListedWithTheirIds() throws Exception {
        givenEditorOfProjectWithTeams(team(7, "Lighting"), team(3, "Camera"));

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/invitations/teams")
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                // Sorted by name, as the web's select is — Camera before Lighting,
                // which is not the order they were assigned in.
                .andExpect(jsonPath("$._embedded.['scripty:inviteTeams'][0].name").value("Camera"))
                .andExpect(jsonPath("$._embedded.['scripty:inviteTeams'][0].id").value(3))
                .andExpect(jsonPath("$._embedded.['scripty:inviteTeams'][1].name").value("Lighting"))
                // The id is the whole point: it is what `sendInvitation` needs.
                .andExpect(jsonPath("$._embedded.['scripty:inviteTeams'][1].id").value(7));
    }

    @Test
    void aProjectWithNoTeamsAnswersWithAnEmptyListRatherThanAnError() throws Exception {
        givenEditorOfProjectWithTeams();

        // A real answer, and one a client must be able to reach: it is how the
        // app knows to say "assign this project to a team first" instead of
        // offering a form that cannot be submitted.
        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/invitations/teams")
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded").doesNotExist());
    }

    @Test
    void theListIsAdvertisedOnTheInvitationCollection() throws Exception {
        givenEditorOfProjectWithTeams(team(7, "Lighting"));

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/invitations")
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:inviteTeams'].href").exists());
    }

    @Test
    void aReaderCannotSeeTheTeams() throws Exception {
        // Same gate as the invitation list itself: choosing a team is part of
        // sending, and sending is an editor's business.
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/invitations/teams")
                        .accept(MediaTypes.HAL_JSON).with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
