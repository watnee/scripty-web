package com.scripty.service;

public interface ProjectUndoRedoService {

    record UndoRedoResult(boolean success, boolean moveOnly) {
        public static UndoRedoResult failed() {
            return new UndoRedoResult(false, false);
        }

        public static UndoRedoResult moveSuccess() {
            return new UndoRedoResult(true, true);
        }

        public static UndoRedoResult snapshotSuccess() {
            return new UndoRedoResult(true, false);
        }
    }

    void recordCheckpoint(Integer projectId);

    void recordCheckpointForBlock(Integer blockId);

    void recordCheckpointForScene(Integer sceneId);

    void recordMoveCheckpoint(Integer blockId, int fromOrder, int toOrder);

    UndoRedoResult undoWithDetails(Integer projectId);

    UndoRedoResult redoWithDetails(Integer projectId);

    boolean undo(Integer projectId);

    boolean redo(Integer projectId);

    boolean canUndo(Integer projectId);

    boolean canRedo(Integer projectId);

    /** True while undo/redo is applying changes (skip duplicate script-edit activity). */
    boolean isRecordingSuppressed();
}
