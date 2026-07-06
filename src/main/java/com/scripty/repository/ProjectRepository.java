package com.scripty.repository;

import com.scripty.dto.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    @EntityGraph(attributePaths = "teams")
    List<Project> findAllByOrderByTitleAsc();

    @EntityGraph(attributePaths = "teams")
    @Query("SELECT p FROM Project p")
    List<Project> findAllWithTeams();

    @EntityGraph(attributePaths = "teams")
    Optional<Project> findWithTeamsById(Integer id);

    @Query("SELECT s.project FROM Scene s WHERE s.id = :sceneId")
    Project findBySceneId(Integer sceneId);
}
