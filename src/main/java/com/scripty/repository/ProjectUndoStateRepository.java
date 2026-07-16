package com.scripty.repository;

import com.scripty.dto.ProjectUndoState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectUndoStateRepository extends JpaRepository<ProjectUndoState, Integer> {

    Optional<ProjectUndoState> findByProjectIdAndEditionKeyAndUserId(Integer projectId,
                                                                     Integer editionKey,
                                                                     Integer userId);
}
