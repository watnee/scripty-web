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

    @Query(value = "SELECT * FROM project WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    Optional<Project> findTrashedById(@Param("id") Integer id);

    @Modifying
    @Query(value = "UPDATE project SET deleted_at = NULL WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int restoreById(@Param("id") Integer id);

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
