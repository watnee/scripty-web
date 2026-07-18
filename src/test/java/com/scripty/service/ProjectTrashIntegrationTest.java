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
import java.util.List;
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

    /** "Delete forever" does not wait for the recovery window. */
    @Test
    void purgeProjectRemovesItImmediatelyAndCascadesToContent() {
        Integer projectId = createProjectWithBlock("Gone On Request");
        projectService.deleteProject(projectId);

        assertTrue(projectService.purgeProject(projectId));

        assertNull(projectService.getTrashedProject(projectId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project WHERE id = ?", Integer.class, projectId));
        assertEquals(0, blockCount(projectId));
    }

    /** The deleted_at guard keeps a live project safe from a stale purge id. */
    @Test
    void purgeProjectRefusesLiveAndUnknownProjects() {
        Integer projectId = createProjectWithBlock("Not In The Trash");

        assertFalse(projectService.purgeProject(projectId));
        assertFalse(projectService.purgeProject(999999));
        assertNotNull(projectService.read(projectId));
        assertEquals(1, blockCount(projectId));
    }

    /**
     * A user only sees trashed projects they could have opened. A project
     * assigned to a team the user is not on stays out of their trash entirely,
     * so its title never leaks — and emptying the trash cannot touch it.
     */
    @Test
    void trashIsScopedToProjectsTheUserCouldOpen() {
        Integer unassignedId = createProjectWithBlock("Open To Everyone");
        Integer otherTeamId = createProjectWithBlock("Someone Else's Team");
        jdbcTemplate.update("INSERT INTO team (name) VALUES ('trash-scope-team')");
        Integer teamId = jdbcTemplate.queryForObject(
                "SELECT id FROM team WHERE name = 'trash-scope-team'", Integer.class);
        jdbcTemplate.update("INSERT INTO project_team (project_id, team_id) VALUES (?, ?)",
                otherTeamId, teamId);

        projectService.deleteProject(unassignedId);
        projectService.deleteProject(otherTeamId);

        User outsider = new User();
        outsider.setEnabled(true);
        outsider.setTeam("some-other-team");

        List<Integer> visible = projectService.getTrashedProjects(outsider).stream()
                .map(Project::getId)
                .toList();
        assertTrue(visible.contains(unassignedId));
        assertFalse(visible.contains(otherTeamId));
        assertNull(projectService.getTrashedProject(otherTeamId, outsider));
        assertNotNull(projectService.getTrashedProject(unassignedId, outsider));

        projectService.emptyTrash(outsider);

        assertNull(projectService.getTrashedProject(unassignedId));
        assertNotNull(projectService.getTrashedProject(otherTeamId));
    }
}
