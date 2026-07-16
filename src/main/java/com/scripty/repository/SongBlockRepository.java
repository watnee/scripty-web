package com.scripty.repository;

import com.scripty.dto.SongBlock;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongBlockRepository extends JpaRepository<SongBlock, Integer> {

    List<SongBlock> findByTextDocumentIdOrderByOrderAsc(Integer textDocumentId);

    int countByTextDocumentId(Integer textDocumentId);
}
