package com.scripty.service;

/**
 * Session-scoped undo/redo for the song block editor, mirroring
 * {@link ProjectUndoRedoService} for the screenplay. A checkpoint snapshots the
 * song's lyric lines; undo/redo swaps the snapshot back in, so the stacks live
 * for as long as the user's session and are per song document.
 */
public interface SongUndoRedoService {

    /** Snapshots the song's current lines before a change, clearing the redo stack. */
    void recordCheckpoint(Integer documentId);

    /** Same as {@link #recordCheckpoint}, resolving the document from one of its blocks. */
    void recordCheckpointForBlock(Integer blockId);

    /** Restores the previous snapshot. Returns false when there is nothing to undo. */
    boolean undo(Integer documentId);

    /** Re-applies the snapshot undone last. Returns false when there is nothing to redo. */
    boolean redo(Integer documentId);

    boolean canUndo(Integer documentId);

    boolean canRedo(Integer documentId);
}
