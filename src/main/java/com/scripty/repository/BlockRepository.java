package com.scripty.repository;

import com.scripty.dto.Block;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, Integer> {

    List<Block> findByProjectIdOrderByOrderAsc(Integer projectId);

    List<Block> findByScriptEditionIdOrderByOrderAsc(Integer scriptEditionId);

    int countByProjectId(Integer projectId);

    int countByScriptEditionId(Integer scriptEditionId);

    Optional<Block> findByProjectIdAndOrder(Integer projectId, Integer order);

    Optional<Block> findByScriptEditionIdAndOrder(Integer scriptEditionId, Integer order);

    List<Block> findBySourceDocumentIdOrderByOrderAsc(Integer sourceDocumentId);

    List<Block> findBySourceDocumentIdAndScriptEditionIdOrderByOrderAsc(Integer sourceDocumentId, Integer scriptEditionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order > :order AND b.scriptEdition.id = :scriptEditionId")
    void incrementOrdersAbove(Integer order, Integer scriptEditionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + :amount WHERE b.order > :order AND b.scriptEdition.id = :scriptEditionId")
    void incrementOrdersAboveBy(Integer order, Integer scriptEditionId, int amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :order AND b.scriptEdition.id = :scriptEditionId")
    void decrementOrdersAbove(Integer order, Integer scriptEditionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order >= :newOrder AND b.order < :currentOrder AND b.scriptEdition.id = :scriptEditionId")
    void incrementOrdersInRange(Integer newOrder, Integer currentOrder, Integer scriptEditionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :currentOrder AND b.order <= :newOrder AND b.scriptEdition.id = :scriptEditionId")
    void decrementOrdersInRange(Integer currentOrder, Integer newOrder, Integer scriptEditionId);

    // Soft-deleted (trashed) blocks are hidden by the entity's @SQLRestriction,
    // so the trash flows below use native SQL to reach them.

    @Query(value = "SELECT * FROM `block` WHERE project_id = :projectId AND deleted_at IS NOT NULL AND deleted_at >= :cutoff ORDER BY deleted_at DESC",
            nativeQuery = true)
    List<Block> findDeletedByProjectIdSince(Integer projectId, java.time.Instant cutoff);

    @Query(value = "SELECT * FROM `block` WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
    Optional<Block> findDeletedById(Integer id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE `block` SET deleted_at = NULL, `order` = :order WHERE id = :id AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int restoreDeletedById(Integer id, Integer order);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM `block` WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff", nativeQuery = true)
    int purgeDeletedBefore(java.time.Instant cutoff);
}
