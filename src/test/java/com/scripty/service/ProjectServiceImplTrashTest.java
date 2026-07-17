package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.repository.UserRepository;
import com.scripty.viewmodel.project.projecttrash.ProjectTrashViewModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the delete → trash → restore/purge lifecycle for screenplays. */
class ProjectServiceImplTrashTest {

    private static final int PROJECT_ID = 7;
    private static final int RETENTION_DAYS = 30;

    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ProjectActivityService projectActivityService;
    private ProjectServiceImpl service;

    private Project project;
    private User user;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        projectActivityService = mock(ProjectActivityService.class);
        service = new ProjectServiceImpl(
                projectRepository,
                mock(PersonRepository.class),
                mock(BlockRepository.class),
                mock(TeamRepository.class),
                mock(UserService.class),
                projectActivityService,
                mock(ScriptEditionService.class),
                userRepository);
        ReflectionTestUtils.setField(service, "trashRetentionDays", RETENTION_DAYS);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("The Big Screenplay");

        user = new User();
        user.setId(3);
        user.setUsername("writer");
        user.setEnabled(true);
        user.setWriter(true);
    }

    /** Enabled, but with no privileged role — access comes down to team membership. */
    private static User teamOnlyUser(String team) {
        User outsider = new User();
        outsider.setId(9);
        outsider.setUsername("outsider");
        outsider.setEnabled(true);
        outsider.setTeam(team);
        return outsider;
    }

    @Test
    void deleteMarksTheScreenplayTrashedInsteadOfRemovingTheRow() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        Project deleted = service.deleteProject(PROJECT_ID);

        assertNotNull(deleted);
        assertTrue(deleted.isTrashed(), "delete should stamp deletedAt");
        verify(projectRepository).markTrashed(eq(PROJECT_ID), any(LocalDateTime.class));
        // The row and everything cascading off it must survive — that's the recovery window.
        verify(projectRepository, never()).delete(any(Project.class));
        verify(projectRepository, never()).purgeTrashed(anyInt());
    }

    @Test
    void deleteClearsTheScreenplayAsAnyonesDefaultProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        service.deleteProject(PROJECT_ID);

        // The FK only nulls on a real delete, so a trashed screenplay would otherwise stay
        // someone's default and their dashboard would resolve to something they can't open.
        verify(userRepository).clearDefaultProject(PROJECT_ID);
    }

    @Test
    void deleteRecordsActivityAgainstTheProject() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        service.deleteProject(PROJECT_ID);

        verify(projectActivityService).recordForCurrentUser(
                eq(PROJECT_ID),
                eq(ProjectActivity.ACTION_PROJECT_DELETED),
                contains("The Big Screenplay"),
                eq(ProjectActivity.ENTITY_PROJECT),
                eq(PROJECT_ID));
    }

    @Test
    void deleteIsANoOpForAnUnknownId() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertNull(service.deleteProject(PROJECT_ID));
        assertNull(service.deleteProject(null));
        verify(projectRepository, never()).markTrashed(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void trashListsTrashedScreenplaysWithTheirPurgeDate() {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(2);
        project.setDeletedAt(deletedAt);
        when(projectRepository.findTrashed()).thenReturn(List.of(project));

        ProjectTrashViewModel vm = service.getProjectTrashViewModel(user);

        assertEquals(1, vm.getProjects().size());
        assertEquals(RETENTION_DAYS, vm.getRetentionDays());
        assertEquals("The Big Screenplay", vm.getProjects().get(0).getTitle());
        assertEquals(deletedAt, vm.getProjects().get(0).getDeletedAt());
        assertEquals(deletedAt.plusDays(RETENTION_DAYS), vm.getProjects().get(0).getPurgesAt());
    }

    @Test
    void trashHidesScreenplaysTheUserCannotAccess() {
        project.setDeletedAt(LocalDateTime.now());
        project.getTeams().add(teamNamed("Other Team"));
        when(projectRepository.findTrashed()).thenReturn(List.of(project));

        assertTrue(service.getProjectTrashViewModel(teamOnlyUser("My Team")).getProjects().isEmpty());
    }

    @Test
    void restoreClearsDeletedAt() {
        project.setDeletedAt(LocalDateTime.now());
        when(projectRepository.findTrashedById(PROJECT_ID)).thenReturn(Optional.of(project));

        Project restored = service.restoreProject(PROJECT_ID, user);

        assertNotNull(restored);
        assertFalse(restored.isTrashed());
        verify(projectRepository).markRestored(PROJECT_ID);
        verify(projectActivityService).recordForCurrentUser(
                eq(PROJECT_ID),
                eq(ProjectActivity.ACTION_PROJECT_RESTORED),
                contains("The Big Screenplay"),
                eq(ProjectActivity.ENTITY_PROJECT),
                eq(PROJECT_ID));
    }

    @Test
    void restoreRefusesAScreenplayThatIsNotInTheTrash() {
        when(projectRepository.findTrashedById(PROJECT_ID)).thenReturn(Optional.empty());

        assertNull(service.restoreProject(PROJECT_ID, user));
        verify(projectRepository, never()).markRestored(anyInt());
    }

    @Test
    void purgeRemovesOnlyTrashedScreenplays() {
        project.setDeletedAt(LocalDateTime.now());
        when(projectRepository.findTrashedById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectRepository.purgeTrashed(PROJECT_ID)).thenReturn(1);

        assertTrue(service.purgeProject(PROJECT_ID, user));
        verify(projectRepository).purgeTrashed(PROJECT_ID);
    }

    @Test
    void purgeRefusesAScreenplayThatIsNotInTheTrash() {
        // Purge is reachable only from the trash — a live screenplay must never be purged
        // straight from the list, or deleting would skip the recovery window entirely.
        when(projectRepository.findTrashedById(PROJECT_ID)).thenReturn(Optional.empty());

        assertFalse(service.purgeProject(PROJECT_ID, user));
        verify(projectRepository, never()).purgeTrashed(anyInt());
    }

    @Test
    void purgeRefusesAScreenplayTheUserCannotAccess() {
        project.setDeletedAt(LocalDateTime.now());
        project.getTeams().add(teamNamed("Other Team"));
        when(projectRepository.findTrashedById(PROJECT_ID)).thenReturn(Optional.of(project));

        assertFalse(service.purgeProject(PROJECT_ID, teamOnlyUser("My Team")));
        verify(projectRepository, never()).purgeTrashed(anyInt());
    }

    @Test
    void purgeExpiredRemovesScreenplaysPastTheRetentionWindow() {
        when(projectRepository.purgeTrashed(PROJECT_ID)).thenReturn(1);
        when(projectRepository.findTrashedBefore(any(LocalDateTime.class))).thenReturn(List.of(project));

        assertEquals(1, service.purgeExpiredProjects());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(projectRepository).findTrashedBefore(cutoff.capture());
        // The cutoff is the retention window back from now, give or take the test's own runtime.
        LocalDateTime expected = LocalDateTime.now().minusDays(RETENTION_DAYS);
        assertTrue(cutoff.getValue().isAfter(expected.minusMinutes(1))
                        && cutoff.getValue().isBefore(expected.plusMinutes(1)),
                "cutoff should be " + RETENTION_DAYS + " days back, was " + cutoff.getValue());
    }

    @Test
    void purgeExpiredLeavesScreenplaysInsideTheRetentionWindow() {
        when(projectRepository.findTrashedBefore(any(LocalDateTime.class))).thenReturn(List.of());

        assertEquals(0, service.purgeExpiredProjects());
        verify(projectRepository, never()).purgeTrashed(anyInt());
    }

    private static com.scripty.dto.Team teamNamed(String name) {
        com.scripty.dto.Team team = new com.scripty.dto.Team();
        team.setName(name);
        return team;
    }
}
