package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers the soft-delete lifecycle against a real database: the @SQLRestriction
 * that hides trashed projects, the native queries that deliberately bypass it,
 * and the purge relying on the schema's ON DELETE CASCADE to remove content.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectTrashIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectPurgeService projectPurgeService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Integer createProjectWithBlock(String title) {
        Project project = new Project();
        project.setTitle(title);
        Integer projectId = projectRepository.save(project).getId();
        jdbcTemplate.update(
                "INSERT INTO block (`order`, content, `type`, project_id) VALUES (1, 'A line of action.', 'ACTION', ?)",
                projectId);
        return projectId;
    }

    private int blockCount(Integer projectId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM block WHERE project_id = ?", Integer.class, projectId);
        return count == null ? 0 : count;
    }

    private void backdateDeletedAt(Integer projectId, int daysAgo) {
        jdbcTemplate.update("UPDATE project SET deleted_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(daysAgo), projectId);
    }

    @Test
    void deletingHidesProjectButKeepsItRecoverable() {
        Integer projectId = createProjectWithBlock("Hidden But Kept");

        projectService.deleteProject(projectId);

        // Invisible to ordinary reads...
        assertNull(projectService.read(projectId));
        assertFalse(projectRepository.findById(projectId).isPresent());
        assertFalse(projectRepository.findAllWithTeams().stream()
                .anyMatch(p -> projectId.equals(p.getId())));

        // ...but still in the trash, with its content intact.
        assertNotNull(projectService.getTrashedProject(projectId));
        assertEquals(1, blockCount(projectId));
    }

    /**
     * The column's ON DELETE SET NULL only fires on the purge, so without an
     * explicit clear a user would keep defaulting to a project in the trash.
     */
    @Test
    void deletingClearsTheProjectAsAnyonesDefault() {
        Integer projectId = createProjectWithBlock("Someone's Default");
        User user = userRepository.findByUsername("admin").orElseThrow();
        user.setDefaultProjectId(projectId);
        userRepository.save(user);

        projectService.deleteProject(projectId);

        assertNull(userRepository.findByUsername("admin").orElseThrow().getDefaultProjectId());
    }

    /** Other users' defaults are untouched. */
    @Test
    void deletingLeavesDefaultsPointingAtOtherProjects() {
        Integer keptId = createProjectWithBlock("Kept Default");
        Integer doomedId = createProjectWithBlock("Doomed");
        User user = userRepository.findByUsername("admin").orElseThrow();
        user.setDefaultProjectId(keptId);
        userRepository.save(user);

        projectService.deleteProject(doomedId);

        assertEquals(keptId, userRepository.findByUsername("admin").orElseThrow().getDefaultProjectId());
    }

    @Test
    void restoreBringsBackProjectAndContent() {
        Integer projectId = createProjectWithBlock("Back From The Dead");
        projectService.deleteProject(projectId);

        assertTrue(projectService.restoreProject(projectId));

        Project restored = projectService.read(projectId);
        assertNotNull(restored);
        assertNull(restored.getDeletedAt());
        assertEquals(1, blockCount(projectId));
        assertNull(projectService.getTrashedProject(projectId));
    }

    @Test
    void restoringAProjectThatIsNotTrashedReportsFailure() {
        Integer projectId = createProjectWithBlock("Never Deleted");

        assertFalse(projectService.restoreProject(projectId));
        assertFalse(projectService.restoreProject(999999));
    }

    @Test
    void purgeRemovesProjectsPastTheRecoveryWindowAndCascadesToContent() {
        Integer projectId = createProjectWithBlock("Past The Window");
        projectService.deleteProject(projectId);
        backdateDeletedAt(projectId, projectPurgeService.getRetentionDays() + 1);

        projectPurgeService.purgeExpiredProjects();

        assertNull(projectService.getTrashedProject(projectId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project WHERE id = ?", Integer.class, projectId));
        // The FK cascade, not application code, is what removes the blocks.
        assertEquals(0, blockCount(projectId));
    }

    @Test
    void purgeSparesProjectsInsideTheRecoveryWindow() {
        Integer projectId = createProjectWithBlock("Still In The Window");
        projectService.deleteProject(projectId);
        backdateDeletedAt(projectId, projectPurgeService.getRetentionDays() - 1);

        projectPurgeService.purgeExpiredProjects();

        assertNotNull(projectService.getTrashedProject(projectId));
        assertEquals(1, blockCount(projectId));
    }

    @Test
    void purgeLeavesLiveProjectsAlone() {
        Integer projectId = createProjectWithBlock("Very Much Alive");

        projectPurgeService.purgeExpiredProjects();

        assertNotNull(projectService.read(projectId));
        assertEquals(1, blockCount(projectId));
    }

    @Test
    void restoreProjectsBringsBackTheSelectedIdsOnly() {
        Integer restoredId = createProjectWithBlock("Restore Me");
        Integer leftId = createProjectWithBlock("Leave Me");
        projectService.deleteProject(restoredId);
        projectService.deleteProject(leftId);

        assertEquals(1, projectService.restoreProjects(java.util.List.of(restoredId, 999999)));

        assertNotNull(projectService.read(restoredId));
        assertNull(projectService.read(leftId));
        assertNotNull(projectService.getTrashedProject(leftId));
    }

    @Test
    void restoreAllTrashedBringsBackEveryTrashedProject() {
        Integer firstId = createProjectWithBlock("First Trashed");
        Integer secondId = createProjectWithBlock("Second Trashed");
        projectService.deleteProject(firstId);
        projectService.deleteProject(secondId);
        long before = projectService.getTrashedProjectCount();

        int restored = projectService.restoreAllTrashed();

        assertTrue(restored >= 2, "expected at least the two just-trashed projects");
        assertEquals(before, restored);
        assertNotNull(projectService.read(firstId));
        assertNotNull(projectService.read(secondId));
        assertEquals(0, projectService.getTrashedProjectCount());
    }

    @Test
    void purgeProjectsDeletesImmediatelyAndCascadesToContent() {
        Integer projectId = createProjectWithBlock("Purge Me Now");
        projectService.deleteProject(projectId);

        // No backdating: an admin purge skips the recovery window entirely.
        assertEquals(1, projectService.purgeProjects(java.util.List.of(projectId)));

        assertNull(projectService.getTrashedProject(projectId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project WHERE id = ?", Integer.class, projectId));
        assertEquals(0, blockCount(projectId));
    }

    @Test
    void purgeProjectsRefusesToTouchLiveProjects() {
        Integer liveId = createProjectWithBlock("Not In The Trash");

        assertEquals(0, projectService.purgeProjects(java.util.List.of(liveId)));

        assertNotNull(projectService.read(liveId));
        assertEquals(1, blockCount(liveId));
    }

    @Test
    void getTrashedProjectCountCountsOnlyTrashedProjects() {
        Integer liveId = createProjectWithBlock("Alive");
        Integer trashedId = createProjectWithBlock("Trashed");
        long before = projectService.getTrashedProjectCount();

        projectService.deleteProject(trashedId);

        assertEquals(before + 1, projectService.getTrashedProjectCount());
        assertNotNull(projectService.read(liveId));
    }
}
