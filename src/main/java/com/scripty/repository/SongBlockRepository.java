package com.scripty.repository;

import com.scripty.dto.SongBlock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** Trashed lines whose retention window has lapsed, for the nightly purge. */
    List<SongBlock> findByDeletedAtNotNullAndDeletedAtBefore(LocalDateTime cutoff);

    int countByTextDocumentId(Integer textDocumentId);

    List<SongBlock> findBySongEditionIdOrderByOrderAsc(Integer songEditionId);

    /** A song version's live lines, in order. Excludes soft-deleted (trashed) lines. */
    List<SongBlock> findBySongEditionIdAndDeletedAtIsNullOrderByOrderAsc(Integer songEditionId);

    int countBySongEditionId(Integer songEditionId);
}
