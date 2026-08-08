package com.scripty.repository;

import com.scripty.dto.DeletedBlock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The block trash. Each row is a recovery copy of a deleted {@link com.scripty.dto.Block};
 * see {@link DeletedBlock} for why the trash is a separate table rather than a flag.
 */
public interface DeletedBlockRepository extends JpaRepository<DeletedBlock, Integer> {

    List<DeletedBlock> findByProjectIdOrderByDeletedAtDesc(Integer projectId);

    int countByProjectId(Integer projectId);

    Optional<DeletedBlock> findByIdAndProjectId(Integer id, Integer projectId);

    /**
     * The ids of trashed blocks past the retention window, oldest first and at
     * most a page at a time, for the nightly purge.
     *
     * <p>Ids and a page, not rows and all of them. Each row carries the deleted
     * block's text, and the backlog is whatever a month of editing left behind —
     * so the finder that returned entities sized the job's memory to the
     * writing, and a purge that had been missed for a few nights was the run
     * most likely to fall over. See {@code BlockTrashServiceImpl.purgeExpired}.
     */
    @Query("SELECT d.id FROM DeletedBlock d WHERE d.deletedAt < :cutoff ORDER BY d.deletedAt ASC, d.id ASC")
    List<Integer> findIdsDeletedBefore(@Param("cutoff") LocalDateTime cutoff, Pageable batch);

    /** Deletes one batch of trash outright, in a single statement. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DeletedBlock d WHERE d.id IN :ids")
    int deleteAllByIdIn(@Param("ids") List<Integer> ids);
}
