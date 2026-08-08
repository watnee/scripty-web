package com.scripty.repository;

import com.scripty.dto.TextDocument;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Everything filed under one folder, whatever state it is in.
     *
     * <p>Deliberately not narrowed to the working list: deleting a folder has
     * to unfile a document sitting in the trash or the archive too, or it would
     * come back pointing at a folder that no longer exists.
     *
     * <p>Spelled {@code Folder_Id} rather than {@code FolderId}: the entity's
     * attribute is the folder itself, and the plain spelling asks for a
     * {@code folderId} attribute that does not exist — which fails at startup,
     * taking every bean that depends on this repository with it. The underscore
     * is what says "the id of the folder".
     */
    List<TextDocument> findByFolder_Id(Integer folderId);

    Optional<TextDocument> findByIdAndDeletedAtIsNull(Integer id);

    Optional<TextDocument> findByIdAndProjectIdAndDeletedAtIsNull(Integer id, Integer projectId);

    int countByProjectIdAndDeletedAtIsNull(Integer projectId);

    List<TextDocument> findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Integer projectId);

    int countByProjectIdAndDeletedAtIsNotNull(Integer projectId);

    int countByProjectIdAndDocumentTypeAndDeletedAtIsNotNull(Integer projectId, String documentType);

    Optional<TextDocument> findByIdAndProjectIdAndDeletedAtIsNotNull(Integer id, Integer projectId);

    /**
     * The ids of trashed documents past their retention window, oldest first
     * and at most a page at a time, for the nightly purge.
     *
     * <p>Ids and a page, not whole documents: every row here carries its lyric
     * or note as {@code content}, so the finder that returned entities made the
     * job's memory a function of how much was thrown away that month. Mirrors
     * {@link DeletedBlockRepository#findIdsDeletedBefore}.
     */
    @Query("SELECT d.id FROM TextDocument d WHERE d.deletedAt < :cutoff"
            + " ORDER BY d.deletedAt ASC, d.id ASC")
    List<Integer> findIdsDeletedBefore(@Param("cutoff") LocalDateTime cutoff, Pageable batch);

    /**
     * Deletes one batch of trashed documents outright, in a single statement.
     *
     * <p>A document's lines, versions, editions, recordings and undo stacks go
     * with it because every one of those tables references {@code text_document}
     * {@code ON DELETE CASCADE} — the same thing that carried them away when the
     * purge deleted entities one at a time, since nothing here cascades in the
     * mapping.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM TextDocument d WHERE d.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<Integer> ids);

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
