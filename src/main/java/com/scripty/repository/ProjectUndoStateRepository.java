package com.scripty.repository;

import com.scripty.dto.ProjectUndoState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectUndoStateRepository extends JpaRepository<ProjectUndoState, Integer> {

    Optional<ProjectUndoState> findByProjectIdAndEditionKeyAndUserId(Integer projectId,
                                                                     Integer editionKey,
                                                                     Integer userId);

    /**
     * How long the encoded undo stack is, without fetching it.
     *
     * <p>The Undo button asks "is there anything to undo?" on every editor load
     * and after every undo or redo, and the honest answer is one bit. Fetching
     * the row to get it drags {@code undo_json} across the wire — up to fifty
     * whole-screenplay snapshots — and then parses the lot, for a question the
     * length already answers: the stack is a JSON array, and the empty one is
     * {@code []}. See {@code ProjectUndoRedoServiceImpl.hasEntries}.
     */
    @Query("SELECT LENGTH(s.undoJson) FROM ProjectUndoState s"
            + " WHERE s.project.id = :projectId AND s.editionKey = :editionKey AND s.user.id = :userId")
    Optional<Integer> findUndoStackLength(@Param("projectId") Integer projectId,
                                          @Param("editionKey") Integer editionKey,
                                          @Param("userId") Integer userId);

    /** The redo side of {@link #findUndoStackLength}, and asked for the same reason. */
    @Query("SELECT LENGTH(s.redoJson) FROM ProjectUndoState s"
            + " WHERE s.project.id = :projectId AND s.editionKey = :editionKey AND s.user.id = :userId")
    Optional<Integer> findRedoStackLength(@Param("projectId") Integer projectId,
                                          @Param("editionKey") Integer editionKey,
                                          @Param("userId") Integer userId);
}
