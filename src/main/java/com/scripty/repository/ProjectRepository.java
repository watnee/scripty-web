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

/**
 * {@link Project} carries an {@code @SQLRestriction} of {@code deleted_at is null}, so
 * everything above — including inherited {@code findById} and {@code findAll} — already
 * skips trashed screenplays without asking.
 *
 * <p>The native queries below are the deliberate way back in, for the trash view and the
 * purge job. They must stay native: Hibernate applies the restriction to JPQL, so a JPQL
 * query for trashed projects would contradict itself and always come back empty. The state
 * transitions are writes rather than load-mutate-save for the same reason — merging an
 * entity the restriction hides would have Hibernate treat it as detached.
 */
public interface ProjectRepository extends JpaRepository<Project, Integer> {

    @EntityGraph(attributePaths = "teams")
    List<Project> findAllByOrderByTitleAsc();

    @EntityGraph(attributePaths = "teams")
    @Query("SELECT p FROM Project p")
    List<Project> findAllWithTeams();

    @EntityGraph(attributePaths = "teams")
    Optional<Project> findWithTeamsById(Integer id);

    @Query(value = "SELECT * FROM project WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC",
            nativeQuery = true)
    List<Project> findTrashed();

    @Query(value = "SELECT * FROM project WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Project> findTrashedById(@Param("id") Integer id);

    /** Trashed screenplays past their retention window, for the purge job. */
    @Query(value = "SELECT * FROM project WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff",
            nativeQuery = true)
    List<Project> findTrashedBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query(value = "UPDATE project SET deleted_at = :now WHERE id = :id AND deleted_at IS NULL",
            nativeQuery = true)
    int markTrashed(@Param("id") Integer id, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = "UPDATE project SET deleted_at = NULL WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int markRestored(@Param("id") Integer id);

    /**
     * Purges for good. Child rows go with it via the {@code ON DELETE CASCADE} FKs the schema
     * already declares, and the two {@code ON DELETE SET NULL} FKs — invitations and
     * {@code user.default_project_id} — clear themselves the same way.
     */
    @Modifying
    @Query(value = "DELETE FROM project WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int purgeTrashed(@Param("id") Integer id);
}
