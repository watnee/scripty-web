package com.scripty.repository;

import com.scripty.dto.Scene;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SceneRepository extends JpaRepository<Scene, Integer> {

    List<Scene> findByProjectIdOrderByOrderAsc(Integer projectId);

    int countByProjectId(Integer projectId);

    Optional<Scene> findByProjectIdAndOrder(Integer projectId, Integer order);

    @Modifying
    @Query("UPDATE Scene s SET s.order = s.order + 1 WHERE s.order > :order AND s.project.id = :projectId")
    void incrementOrdersAbove(Integer order, Integer projectId);

    @Modifying
    @Query("UPDATE Scene s SET s.order = s.order - 1 WHERE s.order > :order AND s.project.id = :projectId")
    void decrementOrdersAbove(Integer order, Integer projectId);
}
