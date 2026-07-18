package com.scripty.service;

import com.scripty.dto.SongVersion;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;

/**
 * Restorable snapshots of a song version, mirroring {@link ProjectVersionService}
 * for the screenplay. Snapshots are scoped to a {@link com.scripty.dto.SongEdition}
 * so each song version keeps its own history. A song's undo/redo stacks live in
 * the session ({@link SongUndoRedoService}); these snapshots are persistent.
 */
public interface SongVersionService {

    SongVersionHistoryViewModel getVersionHistoryViewModel(Integer documentId, Integer editionId);

    /** Saves a labelled snapshot of the song version's current state. */
    SongVersion createVersion(Integer documentId, Integer editionId, String label);

    /**
     * Saves an auto-labelled snapshot, coalescing into the most recent auto-save
     * when it is still fresh, and skipping entirely when nothing changed.
     */
    void autoSaveVersion(Integer documentId, Integer editionId);

    /** Auto-saves the song version owning {@code blockId}. */
    void autoSaveVersionForBlock(Integer blockId);

    String buildSnapshotJson(Integer documentId, Integer editionId);

    /**
     * Restores a snapshot onto its song version, saving a "Before restore"
     * snapshot first. Only restores when the version belongs to {@code editionId}.
     *
     * @return true if restored, false when missing or belonging to another version
     */
    boolean restoreVersionForDocument(Integer versionId, Integer editionId);

    /**
     * Deletes a snapshot only when it belongs to {@code editionId}.
     *
     * @return true if deleted, false when missing or belonging to another version
     */
    boolean deleteVersionForDocument(Integer versionId, Integer editionId);
}
