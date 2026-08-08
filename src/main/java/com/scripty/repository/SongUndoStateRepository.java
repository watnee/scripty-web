package com.scripty.repository;

import com.scripty.dto.SongUndoState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SongUndoStateRepository extends JpaRepository<SongUndoState, Integer> {

    Optional<SongUndoState> findByTextDocumentIdAndSongEditionIdAndUserId(Integer textDocumentId,
                                                                          Integer songEditionId,
                                                                          Integer userId);

    /**
     * How long the encoded undo stack is, without fetching it — the song
     * editor's counterpart to
     * {@link ProjectUndoStateRepository#findUndoStackLength}, and there for the
     * same reason: the Undo button wants one bit, not fifty snapshots.
     */
    @Query("SELECT LENGTH(s.undoJson) FROM SongUndoState s"
            + " WHERE s.textDocument.id = :documentId AND s.songEdition.id = :editionId"
            + " AND s.user.id = :userId")
    Optional<Integer> findUndoStackLength(@Param("documentId") Integer documentId,
                                          @Param("editionId") Integer editionId,
                                          @Param("userId") Integer userId);

    /** The redo side of {@link #findUndoStackLength}. */
    @Query("SELECT LENGTH(s.redoJson) FROM SongUndoState s"
            + " WHERE s.textDocument.id = :documentId AND s.songEdition.id = :editionId"
            + " AND s.user.id = :userId")
    Optional<Integer> findRedoStackLength(@Param("documentId") Integer documentId,
                                          @Param("editionId") Integer editionId,
                                          @Param("userId") Integer userId);
}
