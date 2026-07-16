package com.scripty.service;

import com.scripty.dto.SongVersion;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;

/**
 * Restorable snapshots of a song, mirroring {@link ProjectVersionService} for
 * the screenplay. A song's undo/redo stacks live in the session
 * ({@link SongUndoRedoService}); these snapshots are persistent.
 */
public interface SongVersionService {

    SongVersionHistoryViewModel getVersionHistoryViewModel(Integer documentId);

    /** Saves a labelled snapshot of the song's current state. */
    SongVersion createVersion(Integer documentId, String label);

    /**
     * Saves an auto-labelled snapshot, coalescing into the most recent auto-save
     * when it is still fresh, and skipping entirely when nothing changed.
     */
    void autoSaveVersion(Integer documentId);

    /** Auto-saves the song owning {@code blockId}. */
    void autoSaveVersionForBlock(Integer blockId);

    String buildSnapshotJson(Integer documentId);

    /**
     * Restores a snapshot onto its song, saving a "Before restore" snapshot
     * first. Only restores when the version belongs to {@code documentId}.
     *
     * @return true if restored, false when the version is missing or belongs to another song
     */
    boolean restoreVersionForDocument(Integer versionId, Integer documentId);

    /**
     * Deletes a snapshot only when it belongs to {@code documentId}.
     *
     * @return true if deleted, false when the version is missing or belongs to another song
     */
    boolean deleteVersionForDocument(Integer versionId, Integer documentId);
}
