package com.scripty.repository;

import com.scripty.dto.Block;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, Integer> {

    // Ordering ties break on id so a script with duplicate order values (older
    // data predating the max-order fix) still renders in a stable sequence
    // rather than an arbitrary one that can differ between editor and export.
    List<Block> findByProjectIdOrderByOrderAscIdAsc(Integer projectId);

    List<Block> findByScriptEditionIdOrderByOrderAscIdAsc(Integer scriptEditionId);

    int countByProjectId(Integer projectId);

    int countByScriptEditionId(Integer scriptEditionId);

    // Next order comes from the highest existing order, not the row count: the
    // two only agree while orders are a dense 1..N run, and a count-derived
    // order silently collides with an existing block once they are not.
    @Query("SELECT MAX(b.order) FROM Block b WHERE b.scriptEdition.id = :scriptEditionId")
    Integer findMaxOrderByScriptEditionId(Integer scriptEditionId);

    @Query("SELECT MAX(b.order) FROM Block b WHERE b.project.id = :projectId")
    Integer findMaxOrderByProjectId(Integer projectId);

    Optional<Block> findByProjectIdAndOrder(Integer projectId, Integer order);

    Optional<Block> findByScriptEditionIdAndOrder(Integer scriptEditionId, Integer order);

    List<Block> findBySourceDocumentIdOrderByOrderAscIdAsc(Integer sourceDocumentId);

    List<Block> findBySourceDocumentIdAndScriptEditionIdOrderByOrderAscIdAsc(Integer sourceDocumentId, Integer scriptEditionId);

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
}
