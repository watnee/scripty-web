package com.scripty.repository;

import com.scripty.dto.TextDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextDocumentRepository extends JpaRepository<TextDocument, Integer> {

    List<TextDocument> findByProjectIdOrderBySortOrderAscUpdatedAtDesc(Integer projectId);

    Optional<TextDocument> findByIdAndProjectId(Integer id, Integer projectId);

    int countByProjectId(Integer projectId);
}
