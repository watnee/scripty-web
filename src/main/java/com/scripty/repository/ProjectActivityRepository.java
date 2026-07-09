package com.scripty.repository;

import com.scripty.dto.ProjectActivity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, Integer> {

    @Query("""
            SELECT a FROM ProjectActivity a
            LEFT JOIN FETCH a.actorUser
            WHERE a.project.id = :projectId
            ORDER BY a.createdAt DESC
            """)
    List<ProjectActivity> findRecentByProjectId(@Param("projectId") Integer projectId, Pageable pageable);

    @Query("""
            SELECT a FROM ProjectActivity a
            WHERE a.project.id = :projectId
              AND a.actionType = :actionType
              AND a.actorUser.id = :actorUserId
              AND a.createdAt >= :since
            ORDER BY a.createdAt DESC
            """)
    List<ProjectActivity> findLatestRollupCandidates(
            @Param("projectId") Integer projectId,
            @Param("actionType") String actionType,
            @Param("actorUserId") Integer actorUserId,
            @Param("since") LocalDateTime since,
            Pageable pageable);
}
