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

    // --- Song/text-document-owned blocks: ordering scoped to the text document ---

    List<Block> findByTextDocumentIdOrderByOrderAsc(Integer textDocumentId);

    int countByTextDocumentId(Integer textDocumentId);

    Optional<Block> findByTextDocumentIdAndOrder(Integer textDocumentId, Integer order);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order > :order AND b.textDocument.id = :textDocumentId")
    void incrementOrdersAboveDoc(Integer order, Integer textDocumentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :order AND b.textDocument.id = :textDocumentId")
    void decrementOrdersAboveDoc(Integer order, Integer textDocumentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order >= :newOrder AND b.order < :currentOrder AND b.textDocument.id = :textDocumentId")
    void incrementOrdersInRangeDoc(Integer newOrder, Integer currentOrder, Integer textDocumentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :currentOrder AND b.order <= :newOrder AND b.textDocument.id = :textDocumentId")
    void decrementOrdersInRangeDoc(Integer currentOrder, Integer newOrder, Integer textDocumentId);
}
