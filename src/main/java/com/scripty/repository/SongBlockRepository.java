package com.scripty.repository;

import com.scripty.dto.SongBlock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongBlockRepository extends JpaRepository<SongBlock, Integer> {

    /** The song's live lines, in order. Excludes soft-deleted (trashed) lines. */
    List<SongBlock> findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(Integer textDocumentId);

    /** The song's trashed lines, most recently deleted first. */
    List<SongBlock> findByTextDocumentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Integer textDocumentId);

    /**
     * Every line of the song, live and trashed alike. Used when adopting legacy
     * blocks into a version: a trashed line needs a version too, or restoring it
     * later would leave it orphaned.
     */
    List<SongBlock> findByTextDocumentIdOrderByOrderAsc(Integer textDocumentId);

    /**
     * The ids of trashed lines whose retention window has lapsed, oldest first
     * and at most a page at a time, for the nightly purge.
     *
     * <p>Ids and a page rather than every expired line as an entity: the sweep
     * only ever passed them to a delete, and how many there are is a question
     * about how much everyone wrote last month — not a number this job should
     * have to hold. Mirrors {@link DeletedBlockRepository#findIdsDeletedBefore}.
     */
    @Query("SELECT b.id FROM SongBlock b WHERE b.deletedAt IS NOT NULL AND b.deletedAt < :cutoff"
            + " ORDER BY b.deletedAt ASC, b.id ASC")
    List<Integer> findIdsDeletedBefore(@Param("cutoff") LocalDateTime cutoff, Pageable batch);

    /** Deletes one batch of trashed lines outright, in a single statement. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM SongBlock b WHERE b.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<Integer> ids);

    int countByTextDocumentId(Integer textDocumentId);

    List<SongBlock> findBySongEditionIdOrderByOrderAsc(Integer songEditionId);

    /** A song version's live lines, in order. Excludes soft-deleted (trashed) lines. */
    List<SongBlock> findBySongEditionIdAndDeletedAtIsNullOrderByOrderAsc(Integer songEditionId);

    int countBySongEditionId(Integer songEditionId);
}
