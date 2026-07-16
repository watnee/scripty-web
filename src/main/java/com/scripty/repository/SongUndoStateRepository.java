package com.scripty.repository;

import com.scripty.dto.SongUndoState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongUndoStateRepository extends JpaRepository<SongUndoState, Integer> {

    Optional<SongUndoState> findByTextDocumentIdAndUserId(Integer textDocumentId, Integer userId);
}
