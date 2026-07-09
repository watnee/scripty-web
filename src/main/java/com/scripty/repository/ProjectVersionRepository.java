package com.scripty.repository;

import com.scripty.dto.ProjectVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Integer> {

    List<ProjectVersion> findByProjectIdOrderByCreatedAtDesc(Integer projectId);

    List<ProjectVersion> findByScriptEditionIdOrderByCreatedAtDesc(Integer scriptEditionId);

    ProjectVersion findFirstByProjectIdOrderByCreatedAtDesc(Integer projectId);

    ProjectVersion findFirstByScriptEditionIdOrderByCreatedAtDesc(Integer scriptEditionId);

    @Query("""
            SELECT v FROM ProjectVersion v
            WHERE v.scriptEdition.id = :scriptEditionId
              AND v.label LIKE 'Auto-save%'
            ORDER BY v.createdAt DESC, v.id DESC
            """)
    List<ProjectVersion> findAutoSavesByScriptEditionIdOrderByCreatedAtDesc(
            @Param("scriptEditionId") Integer scriptEditionId);
}
