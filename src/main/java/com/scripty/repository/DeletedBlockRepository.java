package com.scripty.repository;

import com.scripty.dto.DeletedBlock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The block trash. Each row is a recovery copy of a deleted {@link com.scripty.dto.Block};
 * see {@link DeletedBlock} for why the trash is a separate table rather than a flag.
 */
public interface DeletedBlockRepository extends JpaRepository<DeletedBlock, Integer> {

    List<DeletedBlock> findByProjectIdOrderByDeletedAtDesc(Integer projectId);

    int countByProjectId(Integer projectId);

    Optional<DeletedBlock> findByIdAndProjectId(Integer id, Integer projectId);

    /** Trashed blocks past the retention window, for the purge job. */
    List<DeletedBlock> findByDeletedAtBefore(LocalDateTime cutoff);
}
