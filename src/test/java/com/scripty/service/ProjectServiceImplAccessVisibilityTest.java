package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.Team;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.viewmodel.project.projectprofile.ProjectShareUserViewModel;
import com.scripty.viewmodel.user.userprofile.UserProjectAccessViewModel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplAccessVisibilityTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private UserService userService;
    @Mock
    private ProjectActivityService projectActivityService;
    @Mock
    private ScriptEditionService scriptEditionService;

    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectServiceImpl(
                projectRepository,
                personRepository,
                blockRepository,
                teamRepository,
                userService,
                projectActivityService,
                scriptEditionService);
    }

    @Test
    void shareAccessUsersIncludesOpenProjectsAndEditFlags() {
        Project openProject = project("Open Script", 1);
        User writer = enabledUser("writer", "Ada", "Writer");
        writer.setWriter(true);
        User viewer = enabledUser("viewer", "Vic", "Viewer");
        viewer.setTeam("Cast");

        when(projectRepository.findWithTeamsById(1)).thenReturn(Optional.of(openProject));
        when(userService.list()).thenReturn(List.of(writer, viewer));

        List<ProjectShareUserViewModel> users = projectService.getProjectShareAccessUsers(1);

        assertEquals(2, users.size());
        ProjectShareUserViewModel writerVm = users.stream()
                .filter(u -> "Ada Writer".equals(u.getDisplayName()))
                .findFirst()
                .orElseThrow();
        ProjectShareUserViewModel viewerVm = users.stream()
                .filter(u -> "Vic Viewer".equals(u.getDisplayName()))
                .findFirst()
                .orElseThrow();

        assertTrue(writerVm.isCanEdit());
        assertEquals("Can edit", writerVm.getPermissionLabel());
        assertEquals("Writer", writerVm.getAccessLabel());

        assertFalse(viewerVm.isCanEdit());
        assertEquals("View only", viewerVm.getPermissionLabel());
        assertEquals("Cast", viewerVm.getAccessLabel());
    }

    @Test
    void userProjectAccessWriterOnTeamProjectCanEdit() {
        Team cast = team(10, "Cast");
        Project teamProject = project("Team Script", 2);
        teamProject.setTeams(List.of(cast));
        Project otherTeamProject = project("Other Script", 3);
        otherTeamProject.setTeams(List.of(team(11, "Crew")));

        User writer = enabledUser("writer", "Ada", "Writer");
        writer.setWriter(true);
        writer.setTeam("Cast");

        when(projectRepository.findAllWithTeams()).thenReturn(List.of(teamProject, otherTeamProject));

        List<UserProjectAccessViewModel> access = projectService.getUserProjectAccess(writer);

        assertEquals(2, access.size());
        assertTrue(access.stream().allMatch(UserProjectAccessViewModel::isCanEdit));
        assertTrue(access.stream().allMatch(a -> "Can edit".equals(a.getPermissionLabel())));
        assertEquals("Writer", access.get(0).getAccessReason());
    }

    @Test
    void userProjectAccessTeamOnlyUserIsViewOnlyOnMatchingProjects() {
        Team cast = team(10, "Cast");
        Project matching = project("Cast Script", 4);
        matching.setTeams(List.of(cast));
        Project other = project("Crew Script", 5);
        other.setTeams(List.of(team(11, "Crew")));
        Project open = project("Open Script", 6);

        User teamUser = enabledUser("castmate", "Casey", "Cast");
        teamUser.setTeam("Cast");

        when(projectRepository.findAllWithTeams()).thenReturn(List.of(matching, other, open));

        List<UserProjectAccessViewModel> access = projectService.getUserProjectAccess(teamUser);

        assertEquals(2, access.size());
        assertTrue(access.stream().noneMatch(UserProjectAccessViewModel::isCanEdit));
        assertTrue(access.stream().allMatch(a -> "View only".equals(a.getPermissionLabel())));

        UserProjectAccessViewModel castAccess = access.stream()
                .filter(a -> "Cast Script".equals(a.getProjectName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Cast", castAccess.getAccessReason());

        UserProjectAccessViewModel openAccess = access.stream()
                .filter(a -> "Open Script".equals(a.getProjectName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Open project", openAccess.getAccessReason());
    }

    @Test
    void userProjectAccessDirectorSeesAllProjectsViewOnly() {
        Project a = project("Alpha", 7);
        a.setTeams(List.of(team(10, "Cast")));
        Project b = project("Beta", 8);

        User director = enabledUser("director", "Dana", "Director");
        director.setDirector(true);

        when(projectRepository.findAllWithTeams()).thenReturn(List.of(a, b));

        List<UserProjectAccessViewModel> access = projectService.getUserProjectAccess(director);

        assertEquals(2, access.size());
        assertTrue(access.stream().noneMatch(UserProjectAccessViewModel::isCanEdit));
        assertTrue(access.stream().allMatch(p -> "Director".equals(p.getAccessReason())));
        assertTrue(access.stream().allMatch(p -> "View only".equals(p.getPermissionLabel())));
    }

    @Test
    void userProjectAccessDisabledUserHasNone() {
        User disabled = enabledUser("gone", "Gone", "User");
        disabled.setEnabled(false);

        List<UserProjectAccessViewModel> access = projectService.getUserProjectAccess(disabled);

        assertTrue(access.isEmpty());
    }

    private static Project project(String title, int id) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        return project;
    }

    private static Team team(int id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        return team;
    }

    private static User enabledUser(String username, String first, String last) {
        User user = new User();
        user.setId(username.hashCode());
        user.setUsername(username);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEnabled(true);
        return user;
    }
}
