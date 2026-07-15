package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTrashTest {

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

    private Project project(Integer id) {
        Project project = new Project();
        project.setId(id);
        project.setTitle("Script " + id);
        return project;
    }

    private User admin() {
        User user = new User();
        user.setUsername("admin");
        user.setEnabled(true);
        user.setAdmin(true);
        return user;
    }

    @Test
    void deleteProjectSoftDeletesInsteadOfRemoving() {
        Project project = project(1);
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));

        Project deleted = projectService.deleteProject(1);

        assertNotNull(deleted.getDeletedAt());
        verify(projectRepository).save(project);
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    void deleteProjectIsIdempotentForAlreadyTrashedProject() {
        Project project = project(1);
        LocalDateTime originallyDeleted = LocalDateTime.now().minusDays(3);
        project.setDeletedAt(originallyDeleted);
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));

        Project deleted = projectService.deleteProject(1);

        assertEquals(originallyDeleted, deleted.getDeletedAt());
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void restoreProjectClearsDeletedAt() {
        Project project = project(1);
        project.setDeletedAt(LocalDateTime.now().minusDays(3));
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));

        Project restored = projectService.restoreProject(1);

        assertNull(restored.getDeletedAt());
        verify(projectRepository).save(project);
    }

    @Test
    void deleteProjectPermanentlyRemovesProject() {
        Project project = project(1);
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));

        projectService.deleteProjectPermanently(1);

        verify(projectRepository).delete(project);
    }

    @Test
    void trashedProjectViewModelsReportDaysUntilPurge() {
        Project recentlyTrashed = project(1);
        recentlyTrashed.setDeletedAt(LocalDateTime.now().minusDays(2));
        Project almostExpired = project(2);
        almostExpired.setDeletedAt(LocalDateTime.now().minusDays(29));
        when(projectRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc())
                .thenReturn(List.of(recentlyTrashed, almostExpired));

        List<ProjectViewModel> trashed = projectService.getTrashedProjectViewModels(admin());

        assertEquals(2, trashed.size());
        assertEquals(28, trashed.get(0).getDaysUntilPurge());
        assertEquals(1, trashed.get(1).getDaysUntilPurge());
    }

    @Test
    void purgeExpiredTrashDeletesOnlyExpiredProjects() {
        Project expired = project(1);
        expired.setDeletedAt(LocalDateTime.now().minusDays(31));
        when(projectRepository.findByDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        int purged = projectService.purgeExpiredTrash();

        assertEquals(1, purged);
        verify(projectRepository).delete(expired);
    }

    @Test
    void trashedProjectHiddenFromProfileView() {
        Project project = project(1);
        project.setDeletedAt(LocalDateTime.now());
        when(projectRepository.findWithTeamsById(1)).thenReturn(Optional.of(project));

        assertNull(projectService.getProjectProfileViewModel(1));
        assertTrue(project.isDeleted());
    }
}
