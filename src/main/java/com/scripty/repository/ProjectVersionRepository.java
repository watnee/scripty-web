package com.scripty.repository;

import com.scripty.dto.ProjectVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Integer> {

    List<ProjectVersion> findByProjectIdOrderByCreatedAtDesc(Integer projectId);

    ProjectVersion findFirstByProjectIdOrderByCreatedAtDesc(Integer projectId);
}
