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
import com.scripty.service.ProjectService;
import com.scripty.service.TeamService;
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
 * The project side of team membership: every team the writer could assign the
 * project to, each flagged assigned-or-not.
 *
 * <p>This is the counterpart to {@link InviteTeamsApiTest}. That one lists only
 * the teams already on the project (the valid invite targets); this lists the
 * whole roster with an {@code assigned} flag so the production page's checkboxes
 * can be drawn and, once ticked, saved back through {@code update}'s
 * {@code teamIds}. As with the invite list, an ordinary editor has no other way
 * to learn team ids — {@code /api/team} is admin-only.
 *
 * <p>Asserted through the real HAL serializer: the embed relation and the
 * {@code assigned} flag are the part a client reads. Get the flag wrong and the
 * picker ticks the wrong boxes, which then unassign on save.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectTeamsApiTest {

    private static final int PROJECT_ID = 1;
    private static final String OPTIONS = "$._embedded.['scripty:projectTeams']";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private TeamService teamService;

    private Team team(Integer id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    /** The whole roster, and which of it this project belongs to. */
    private void givenEditorWhereRosterIsAndProjectHas(List<Team> roster, Team... assigned) {
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(true);
        when(teamService.list()).thenReturn(roster);
        Project project = new Project();
        project.setTeams(List.of(assigned));
        when(projectService.readWithTeams(PROJECT_ID)).thenReturn(project);
    }

    @Test
    void everyTeamIsListedWithWhetherTheProjectBelongsToIt() throws Exception {
        Team camera = team(3, "Camera");
        Team lighting = team(7, "Lighting");
        // The project is on Lighting only; Camera is an option it does not have.
        givenEditorWhereRosterIsAndProjectHas(List.of(camera, lighting), lighting);

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/teams")
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                // Order-independent: the flag is keyed to the team, not a slot.
                .andExpect(jsonPath(OPTIONS + "[?(@.id == 3)].name").value("Camera"))
                .andExpect(jsonPath(OPTIONS + "[?(@.id == 3)].assigned").value(false))
                .andExpect(jsonPath(OPTIONS + "[?(@.id == 7)].name").value("Lighting"))
                .andExpect(jsonPath(OPTIONS + "[?(@.id == 7)].assigned").value(true))
                // Where to send the ticked ids back — a client should not have to
                // know the URL scheme to save its choice.
                .andExpect(jsonPath("$._links.['scripty:update'].href").exists());
    }

    @Test
    void aProjectOnNoTeamsStillListsTheRosterAllUnassigned() throws Exception {
        // Nothing ticked is a real state, and a different one from "no teams
        // exist": the writer can still assign the project to any of them.
        givenEditorWhereRosterIsAndProjectHas(List.of(team(3, "Camera")));

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/teams")
                        .accept(MediaTypes.HAL_JSON).with(user("writer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(OPTIONS + "[?(@.id == 3)].assigned").value(false));
    }

    @Test
    void aReaderCannotSeeTheTeams() throws Exception {
        // Reassigning a project's teams is an editor's call — the same gate the
        // rel is advertised under, and the same one inviteTeams uses.
        when(projectAccess.canEditScript(eq(PROJECT_ID), any(Principal.class))).thenReturn(false);

        mockMvc.perform(get("/api/project/" + PROJECT_ID + "/teams")
                        .accept(MediaTypes.HAL_JSON).with(user("reader").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
