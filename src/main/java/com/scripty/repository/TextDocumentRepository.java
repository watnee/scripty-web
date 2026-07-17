package com.scripty.repository;

import com.scripty.dto.TextDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Documents are soft deleted: {@code deleted_at} marks one as trashed instead of
 * removing the row, so lyrics and version history survive until the trash is purged.
 * Every lookup here says explicitly which side of that line it wants.
 */
public interface TextDocumentRepository extends JpaRepository<TextDocument, Integer> {

    List<TextDocument> findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(Integer projectId);

    Optional<TextDocument> findByIdAndDeletedAtIsNull(Integer id);

    Optional<TextDocument> findByIdAndProjectIdAndDeletedAtIsNull(Integer id, Integer projectId);

    int countByProjectIdAndDeletedAtIsNull(Integer projectId);

    List<TextDocument> findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Integer projectId);

    int countByProjectIdAndDeletedAtIsNotNull(Integer projectId);

    int countByProjectIdAndDocumentTypeAndDeletedAtIsNotNull(Integer projectId, String documentType);

    Optional<TextDocument> findByIdAndProjectIdAndDeletedAtIsNotNull(Integer id, Integer projectId);

    /** Trashed documents past their retention window, for the purge job. */
    List<TextDocument> findByDeletedAtBefore(LocalDateTime cutoff);
}
