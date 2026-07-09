package com.scripty.repository;

import com.scripty.dto.Block;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, Integer> {

    List<Block> findByProjectIdOrderByOrderAsc(Integer projectId);

    int countByProjectId(Integer projectId);

    Optional<Block> findByProjectIdAndOrder(Integer projectId, Integer order);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order > :order AND b.project.id = :projectId")
    void incrementOrdersAbove(Integer order, Integer projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + :amount WHERE b.order > :order AND b.project.id = :projectId")
    void incrementOrdersAboveBy(Integer order, Integer projectId, int amount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :order AND b.project.id = :projectId")
    void decrementOrdersAbove(Integer order, Integer projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order >= :newOrder AND b.order < :currentOrder AND b.project.id = :projectId")
    void incrementOrdersInRange(Integer newOrder, Integer currentOrder, Integer projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :currentOrder AND b.order <= :newOrder AND b.project.id = :projectId")
    void decrementOrdersInRange(Integer currentOrder, Integer newOrder, Integer projectId);
}
