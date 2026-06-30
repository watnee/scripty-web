package com.scripty.repository;

import com.scripty.dto.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    List<Project> findAllByOrderByTitleAsc();

    @Query("SELECT s.project FROM Scene s WHERE s.id = :sceneId")
    Project findBySceneId(Integer sceneId);
}
