package com.scripty.repository;

import com.scripty.dto.TextDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextDocumentRepository extends JpaRepository<TextDocument, Integer> {

    List<TextDocument> findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(Integer projectId);

    List<TextDocument> findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Integer projectId);

    Optional<TextDocument> findByIdAndProjectId(Integer id, Integer projectId);

    List<TextDocument> findByDeletedAtBefore(LocalDateTime cutoff);

    int countByProjectId(Integer projectId);
}
