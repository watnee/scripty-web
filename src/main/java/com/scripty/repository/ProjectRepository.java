package com.scripty.repository;

import com.scripty.dto.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Integer> {

    List<Project> findAllByOrderByTitleAsc();
}
