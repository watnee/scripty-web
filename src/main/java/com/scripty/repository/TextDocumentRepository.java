package com.scripty.repository;

import com.scripty.dto.TextDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Documents are soft deleted: {@code deleted_at} marks one as trashed instead of
 * removing the row, so lyrics and version history survive until the trash is purged.
 * {@code archived_at} is a second, independent stamp meaning "put aside on purpose":
 * hidden from the list, but never expired and still readable by id.
 * Every lookup here says explicitly which side of both lines it wants.
 */
public interface TextDocumentRepository extends JpaRepository<TextDocument, Integer> {

    /**
     * Everything still in the project, archived or not — what a whole-project
     * bundle export takes, since a backup that quietly dropped archived work
     * would not be a backup.
     */
    List<TextDocument> findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(Integer projectId);

    /** The working list: neither trashed nor archived. */
    List<TextDocument> findByProjectIdAndDeletedAtIsNullAndArchivedAtIsNullOrderBySortOrderAscUpdatedAtDesc(
            Integer projectId);

    Optional<TextDocument> findByIdAndDeletedAtIsNull(Integer id);

    Optional<TextDocument> findByIdAndProjectIdAndDeletedAtIsNull(Integer id, Integer projectId);

    int countByProjectIdAndDeletedAtIsNull(Integer projectId);

    List<TextDocument> findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Integer projectId);

    int countByProjectIdAndDeletedAtIsNotNull(Integer projectId);

    int countByProjectIdAndDocumentTypeAndDeletedAtIsNotNull(Integer projectId, String documentType);

    Optional<TextDocument> findByIdAndProjectIdAndDeletedAtIsNotNull(Integer id, Integer projectId);

    /** Trashed documents past their retention window, for the purge job. */
    List<TextDocument> findByDeletedAtBefore(LocalDateTime cutoff);

    // The archive. Nothing expires out of it, so there is no cutoff finder to
    // match findByDeletedAtBefore — only listing, counting and single lookups.

    List<TextDocument> findByProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNullOrderByArchivedAtDesc(
            Integer projectId);

    int countByProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNull(Integer projectId);

    int countByProjectIdAndDocumentTypeAndArchivedAtIsNotNullAndDeletedAtIsNull(
            Integer projectId, String documentType);

    /** One archived document, for unarchiving it. */
    Optional<TextDocument> findByIdAndProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNull(
            Integer id, Integer projectId);

    /** One document still in the working list, for archiving it. */
    Optional<TextDocument> findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(
            Integer id, Integer projectId);
}
