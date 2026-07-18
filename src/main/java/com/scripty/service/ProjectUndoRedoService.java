package com.scripty.service;

public interface ProjectUndoRedoService {

    /**
     * @param blockDelta net change in block count applied by this operation.
     *                   A positive value means blocks were recovered (e.g. an
     *                   undo that restored deleted blocks); zero for moves.
     */
    record UndoRedoResult(boolean success, boolean moveOnly, int blockDelta) {
        public static UndoRedoResult failed() {
            return new UndoRedoResult(false, false, 0);
        }

        public static UndoRedoResult moveSuccess() {
            return new UndoRedoResult(true, true, 0);
        }

        public static UndoRedoResult snapshotSuccess() {
            return new UndoRedoResult(true, false, 0);
        }

        public static UndoRedoResult snapshotSuccess(int blockDelta) {
            return new UndoRedoResult(true, false, blockDelta);
        }
    }

    void recordCheckpoint(Integer projectId);

    void recordCheckpointForBlock(Integer blockId);

    void recordCheckpointForScene(Integer sceneId);

    void recordMoveCheckpoint(Integer blockId, int fromOrder, int toOrder);

    UndoRedoResult undoWithDetails(Integer projectId);

    UndoRedoResult undoWithDetails(Integer projectId, Integer editionId);

    UndoRedoResult redoWithDetails(Integer projectId);

    UndoRedoResult redoWithDetails(Integer projectId, Integer editionId);

    boolean undo(Integer projectId);

    boolean redo(Integer projectId);

    boolean canUndo(Integer projectId);

    boolean canUndo(Integer projectId, Integer editionId);

    boolean canRedo(Integer projectId);

    boolean canRedo(Integer projectId, Integer editionId);

    /** True while undo/redo is applying changes (skip duplicate script-edit activity). */
    boolean isRecordingSuppressed();
}
