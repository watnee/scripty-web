package com.scripty.repository;

import com.scripty.dto.ScriptEdition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScriptEditionRepository extends JpaRepository<ScriptEdition, Integer> {

    List<ScriptEdition> findByProjectIdOrderByNameAsc(Integer projectId);

    Optional<ScriptEdition> findByIdAndProjectId(Integer id, Integer projectId);

    @Query("SELECT e FROM ScriptEdition e WHERE e.project.id = :projectId AND e.isDefault = TRUE")
    Optional<ScriptEdition> findDefaultByProjectId(@Param("projectId") Integer projectId);

    @Query("SELECT e FROM ScriptEdition e WHERE e.project.id = :projectId AND e.isPublished = TRUE")
    Optional<ScriptEdition> findPublishedByProjectId(@Param("projectId") Integer projectId);

    boolean existsByProjectIdAndNameIgnoreCase(Integer projectId, String name);

    long countByProjectId(Integer projectId);
}
