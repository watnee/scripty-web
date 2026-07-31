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
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The project archive against a real database.
 *
 * <p>What separates it from {@link ProjectTrashIntegrationTest} is what these
 * pin down: an archived project is a live row in every way but one. It stays
 * readable by id, keeps its content, is reachable through the access rules, and
 * only the project <em>list</em> leaves it out.
 */
@SpringBootTest
@ActiveProfiles("test")
class ArchivedProjectIntegrationTest {

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

    private boolean listedFor(User user, Integer projectId) {
        for (ProjectViewModel project : projectService.getProjectListViewModel(user.getTeam()).getProjects()) {
            if (projectId.equals(project.getId())) {
                return true;
            }
        }
        return false;
    }

    private User admin() {
        return userRepository.findByUsername("admin").orElseThrow();
    }

    @Test
    void archivingTakesTheProjectOutOfTheListButLeavesItWhole() {
        Integer projectId = createProjectWithBlock("Wrapped Last Spring");

        assertNotNull(projectService.archiveProject(projectId));

        assertFalse(listedFor(admin(), projectId));
        // Still an ordinary live row: readable by id, content intact, and — the
        // point of departure from the trash — visible to plain JPQL.
        Project archived = projectService.read(projectId);
        assertNotNull(archived);
        assertTrue(archived.isArchived());
        assertEquals(1, blockCount(projectId));
        assertNotNull(projectService.getArchivedProject(projectId, admin()));
    }

    @Test
    void unarchivingPutsItBackInTheList() {
        Integer projectId = createProjectWithBlock("Back On The Slate");
        projectService.archiveProject(projectId);

        assertNotNull(projectService.unarchiveProject(projectId));

        assertTrue(listedFor(admin(), projectId));
        assertFalse(projectService.read(projectId).isArchived());
        assertNull(projectService.getArchivedProject(projectId, admin()));
    }

    /** Both directions refuse a project already on the side they would move it to. */
    @Test
    void archivingAndUnarchivingAreNoOpsOnTheWrongSide() {
        Integer projectId = createProjectWithBlock("Only Once");

        assertNull(projectService.unarchiveProject(projectId));
        assertNotNull(projectService.archiveProject(projectId));
        assertNull(projectService.archiveProject(projectId));
        assertNull(projectService.archiveProject(999999));
        assertNull(projectService.unarchiveProject(999999));
    }

    /**
     * Same reasoning as deleting: a default nobody can find in the list would
     * land the user on it at every sign-in with no way back to it.
     */
    @Test
    void archivingClearsTheProjectAsAnyonesDefault() {
        Integer projectId = createProjectWithBlock("Someone's Archived Default");
        User user = admin();
        user.setDefaultProjectId(projectId);
        userRepository.save(user);

        projectService.archiveProject(projectId);

        assertNull(admin().getDefaultProjectId());
    }

    /** Nothing expires out of the archive — the purge job has no opinion on it. */
    @Test
    void thePurgeJobLeavesArchivedProjectsAlone() {
        Integer projectId = createProjectWithBlock("Not On A Clock");
        projectService.archiveProject(projectId);
        jdbcTemplate.update("UPDATE project SET archived_at = ? WHERE id = ?",
                java.time.LocalDateTime.now().minusYears(5), projectId);

        projectPurgeService.purgeExpiredProjects();

        assertNotNull(projectService.read(projectId));
        assertEquals(1, blockCount(projectId));
    }

    /**
     * deleted_at wins. An archived project that is then deleted belongs to the
     * trash alone, so it does not show up twice.
     */
    @Test
    void anArchivedProjectThatIsDeletedLeavesTheArchive() {
        Integer projectId = createProjectWithBlock("Archived Then Binned");
        projectService.archiveProject(projectId);

        projectService.deleteProject(projectId);

        assertNull(projectService.getArchivedProject(projectId, admin()));
        assertNotNull(projectService.getTrashedProject(projectId));
    }

    /**
     * And restoring it brings it back where the person looking for it is
     * looking, rather than dropping it straight back into the archive.
     */
    @Test
    void restoringFromTheTrashAlsoClearsTheArchiveStamp() {
        Integer projectId = createProjectWithBlock("Restored To The List");
        projectService.archiveProject(projectId);
        projectService.deleteProject(projectId);

        assertTrue(projectService.restoreProject(projectId));

        assertFalse(projectService.read(projectId).isArchived());
        assertTrue(listedFor(admin(), projectId));
        assertNull(projectService.getArchivedProject(projectId, admin()));
    }

    /**
     * A user only sees archived projects they could have opened — the same team
     * rule the live list applies, so an archived title never leaks.
     */
    @Test
    void theArchiveIsScopedToProjectsTheUserCouldOpen() {
        Integer unassignedId = createProjectWithBlock("Open To Everyone, Archived");
        Integer otherTeamId = createProjectWithBlock("Another Team's, Archived");
        jdbcTemplate.update("INSERT INTO team (name) VALUES ('archive-scope-team')");
        Integer teamId = jdbcTemplate.queryForObject(
                "SELECT id FROM team WHERE name = 'archive-scope-team'", Integer.class);
        jdbcTemplate.update("INSERT INTO project_team (project_id, team_id) VALUES (?, ?)",
                otherTeamId, teamId);

        projectService.archiveProject(unassignedId);
        projectService.archiveProject(otherTeamId);

        User outsider = new User();
        outsider.setEnabled(true);
        outsider.setTeam("some-other-team");

        List<Integer> visible = projectService.getArchivedProjects(outsider).stream()
                .map(Project::getId)
                .toList();
        assertTrue(visible.contains(unassignedId));
        assertFalse(visible.contains(otherTeamId));
        assertNull(projectService.getArchivedProject(otherTeamId, outsider));
        assertNotNull(projectService.getArchivedProject(unassignedId, outsider));
    }

    /**
     * A bundle export is a backup, so the all-inclusive finder stays that way —
     * the same call the export path builds "everything I can see" from.
     */
    @Test
    void theAllInclusiveFinderStillSeesArchivedProjects() {
        Integer projectId = createProjectWithBlock("In The Backup");
        projectService.archiveProject(projectId);

        assertTrue(projectRepository.findAllWithTeams().stream()
                .anyMatch(p -> projectId.equals(p.getId())));
        assertFalse(projectRepository.findUnarchivedWithTeams().stream()
                .anyMatch(p -> projectId.equals(p.getId())));
    }
}
