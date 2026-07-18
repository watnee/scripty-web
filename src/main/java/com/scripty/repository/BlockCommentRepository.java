package com.scripty.repository;

import com.scripty.dto.BlockComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockCommentRepository extends JpaRepository<BlockComment, Integer> {

    List<BlockComment> findByBlockIdOrderByCreatedAtAsc(Integer blockId);

    long countByBlockId(Integer blockId);

    /**
     * Comment counts for every commented block in a project, as
     * {@code [blockId, count]} rows. Blocks with no comments are omitted, so the
     * badge painter treats a missing id as zero.
     */
    @Query("SELECT c.block.id, COUNT(c) FROM BlockComment c "
            + "WHERE c.block.project.id = :projectId GROUP BY c.block.id")
    List<Object[]> countsByProject(@Param("projectId") Integer projectId);
}
