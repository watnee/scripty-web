package com.scripty.repository;

import com.scripty.dto.ProjectVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Integer> {

    List<ProjectVersion> findByProjectIdOrderByCreatedAtDesc(Integer projectId);

    List<ProjectVersion> findByScriptEditionIdOrderByCreatedAtDesc(Integer scriptEditionId);

    ProjectVersion findFirstByProjectIdOrderByCreatedAtDesc(Integer projectId);

    ProjectVersion findFirstByScriptEditionIdOrderByCreatedAtDesc(Integer scriptEditionId);

    /**
     * The ids of an edition's auto-saves, newest first — everything the prune
     * needs and nothing it does not.
     *
     * <p>Ids rather than rows because this runs after every auto-save, and a
     * {@code ProjectVersion} carries {@code snapshot_json}: the whole screenplay
     * as text. Selecting the entities dragged all thirty retained snapshots into
     * memory on each pass so the prune could read a column of integers off them.
     */
    @Query("""
            SELECT v.id FROM ProjectVersion v
            WHERE v.scriptEdition.id = :scriptEditionId
              AND v.label LIKE 'Auto-save%'
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<Integer> findAutoSaveIdsByScriptEditionIdOrderByCreatedAtDesc(
            @Param("scriptEditionId") Integer scriptEditionId);

    /**
     * Deletes the named versions outright.
     *
     * <p>One statement, where {@code deleteAllById} loads each row and issues a
     * delete per id. Nothing here cascades in the mapping — a version owns no
     * child rows — so there is nothing the entity path would have done that this
     * does not.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProjectVersion v WHERE v.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<Integer> ids);
}
