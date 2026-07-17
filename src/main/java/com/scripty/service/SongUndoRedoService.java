package com.scripty.service;

/**
 * Session-scoped undo/redo for the song block editor, mirroring
 * {@link ProjectUndoRedoService} for the screenplay. A checkpoint snapshots the
 * song's lyric lines; undo/redo swaps the snapshot back in, so the stacks live
 * for as long as the user's session and are per song document.
 */
public interface SongUndoRedoService {

    /** Snapshots the song version's current lines before a change, clearing the redo stack. */
    void recordCheckpoint(Integer documentId, Integer editionId);

    /** Same as {@link #recordCheckpoint}, resolving the document and version from one of its blocks. */
    void recordCheckpointForBlock(Integer blockId);

    /** Restores the previous snapshot. Returns false when there is nothing to undo. */
    boolean undo(Integer documentId, Integer editionId);

    /** Re-applies the snapshot undone last. Returns false when there is nothing to redo. */
    boolean redo(Integer documentId, Integer editionId);

    boolean canUndo(Integer documentId, Integer editionId);

    boolean canRedo(Integer documentId, Integer editionId);
}
