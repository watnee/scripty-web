package com.scripty.repository;

import com.scripty.dto.Project;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    @EntityGraph(attributePaths = "teams")
    List<Project> findAllByOrderByTitleAsc();

    @EntityGraph(attributePaths = "teams")
    @Query("SELECT p FROM Project p")
    List<Project> findAllWithTeams();

    @EntityGraph(attributePaths = "teams")
    Optional<Project> findWithTeamsById(Integer id);

    // The queries below are native so they bypass the @SQLRestriction on Project,
    // which hides trashed rows from every JPQL query. They are the only way to
    // see or act on a project once it has been deleted.

    @Query(value = "SELECT * FROM project WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            nativeQuery = true)
    List<Project> findTrashed();

    /**
     * Trashed projects visible to a member of {@code team}: the ones assigned to
     * that team, plus the unassigned ones everybody can see. Mirrors the team
     * check {@code ProjectServiceImpl} applies to the live project list.
     */
    @Query(value = "SELECT * FROM project p WHERE p.deleted_at IS NOT NULL"
            + " AND (NOT EXISTS (SELECT 1 FROM project_team pt WHERE pt.project_id = p.id)"
            + " OR EXISTS (SELECT 1 FROM project_team pt JOIN team t ON t.id = pt.team_id"
            + " WHERE pt.project_id = p.id AND t.name = :team))"
            + " ORDER BY p.deleted_at DESC",
            nativeQuery = true)
    List<Project> findTrashedForTeam(@Param("team") String team);

    /** Trashed projects with no team assigned — what a user without a team sees. */
    @Query(value = "SELECT * FROM project p WHERE p.deleted_at IS NOT NULL"
            + " AND NOT EXISTS (SELECT 1 FROM project_team pt WHERE pt.project_id = p.id)"
            + " ORDER BY p.deleted_at DESC",
            nativeQuery = true)
    List<Project> findTrashedWithoutTeam();

    @Query(value = "SELECT * FROM project WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Project> findTrashedById(@Param("id") Integer id);

    @Modifying
    @Query(value = "UPDATE project SET deleted_at = NULL WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int restoreById(@Param("id") Integer id);

    /**
     * Hard-deletes one trashed project on request, without waiting for the
     * recovery window. Cascades exactly like {@link #purgeTrashedBefore}. The
     * {@code deleted_at IS NOT NULL} guard keeps a live project safe even if a
     * stale id reaches this query.
     */
    @Modifying
    @Query(value = "DELETE FROM project WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int purgeById(@Param("id") Integer id);

    /**
     * Hard-deletes trashed projects past the recovery window. Every table
     * referencing project(id) does so with ON DELETE CASCADE or SET NULL, so the
     * database removes the project's blocks, documents, editions and the rest.
     */
    @Modifying
    @Query(value = "DELETE FROM project WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff",
            nativeQuery = true)
    int purgeTrashedBefore(@Param("cutoff") LocalDateTime cutoff);
}
