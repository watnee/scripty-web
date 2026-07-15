package com.scripty.repository;

import com.scripty.dto.Project;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    @EntityGraph(attributePaths = "teams")
    List<Project> findAllByDeletedAtIsNullOrderByTitleAsc();

    @EntityGraph(attributePaths = "teams")
    @Query("SELECT p FROM Project p WHERE p.deletedAt IS NULL")
    List<Project> findAllWithTeams();

    @EntityGraph(attributePaths = "teams")
    Optional<Project> findWithTeamsById(Integer id);

    @EntityGraph(attributePaths = "teams")
    List<Project> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    List<Project> findByDeletedAtBefore(LocalDateTime cutoff);
}
