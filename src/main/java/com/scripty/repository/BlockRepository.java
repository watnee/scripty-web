package com.scripty.repository;

import com.scripty.dto.Block;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<Block, Integer> {

    List<Block> findBySceneIdOrderByOrderAsc(Integer sceneId);

    int countBySceneId(Integer sceneId);

    Optional<Block> findBySceneIdAndOrder(Integer sceneId, Integer order);

    @Modifying
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order > :order AND b.scene.id = :sceneId")
    void incrementOrdersAbove(Integer order, Integer sceneId);

    @Modifying
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :order AND b.scene.id = :sceneId")
    void decrementOrdersAbove(Integer order, Integer sceneId);

    @Modifying
    @Query("UPDATE Block b SET b.order = b.order + 1 WHERE b.order >= :newOrder AND b.order < :currentOrder AND b.scene.id = :sceneId")
    void incrementOrdersInRange(Integer newOrder, Integer currentOrder, Integer sceneId);

    @Modifying
    @Query("UPDATE Block b SET b.order = b.order - 1 WHERE b.order > :currentOrder AND b.order <= :newOrder AND b.scene.id = :sceneId")
    void decrementOrdersInRange(Integer currentOrder, Integer newOrder, Integer sceneId);
}
