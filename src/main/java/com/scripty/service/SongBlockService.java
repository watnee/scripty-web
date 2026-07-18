package com.scripty.service;

import com.scripty.dto.SongBlock;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlocksViewModel;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.util.List;

/**
 * CRUD for the ordered lyric blocks that make up a song. Every structural change
 * rebuilds the parent {@link com.scripty.dto.TextDocument} content so existing
 * song features (insert-into-script, share, export) stay in sync.
 */
public interface SongBlockService {

    /**
     * One line in an undo/redo snapshot. Carries the highlight as well as the
     * text, so restoring a snapshot does not drop the song's tints.
     */
    record LineSnapshot(String content, String highlight) {
    }

    SongBlock read(Integer id);

    /** Project id owning the block's document, or null if not found. */
    Integer projectIdForBlock(Integer blockId);

    /** Project id owning the document, or null if not found. */
    Integer projectIdForDocument(Integer documentId);

    /** Document id owning the block, or null if not found. */
    Integer documentIdForBlock(Integer blockId);

    /**
     * Returns the song's blocks, seeding them from the document's free-text
     * content on first access (or a single empty block when there is none).
     */
    List<SongBlockViewModel> getBlocks(Integer documentId);

    /** Appends a new empty block at the end of the song. */
    SongBlock appendBlock(Integer documentId);

    /**
     * Saves {@code afterContent} onto the origin block (when non-null) and
     * inserts a new empty block directly below it.
     */
    SongBlock createBelow(Integer afterBlockId, String afterContent);

    /** Persists new content on a block. */
    SongBlock editContent(Integer blockId, String content);

    /** Sets the background tint on a block; an unknown or blank color clears it. */
    SongBlock setHighlight(Integer blockId, String highlight);

    /**
     * Soft-deletes a block, moving it to the song's "recently deleted lines"
     * recovery list. Keeps at least one (empty) live block in the song.
     */
    Integer deleteBlock(Integer blockId);

    /**
     * The song's soft-deleted lines, newest first, plus the breadcrumbs the
     * recovery page needs. Null when the document does not exist.
     */
    DeletedSongBlocksViewModel getDeletedBlocksViewModel(Integer documentId);

    /**
     * Restores a soft-deleted block to the end of the song. Returns the parent
     * document id, or null if the block is missing or was not trashed.
     */
    Integer restoreBlock(Integer blockId);

    /**
     * Permanently removes a soft-deleted block. Returns the parent document id,
     * or null if the block is missing or is not in the trash.
     */
    Integer purgeBlock(Integer blockId);

    /** Hard-deletes trashed lines past the retention window. Returns the count purged. */
    int purgeExpiredBlocks();

    SongBlock moveUp(Integer blockId);

    SongBlock moveDown(Integer blockId);

    /**
     * Moves a block to {@code position} (zero-based index within the song),
     * clamped to the song's bounds. Backs drag-and-drop reordering.
     */
    SongBlock moveTo(Integer blockId, int position);

    /**
     * The song's lines in order, as an undo/redo snapshot. Null when the
     * document does not exist.
     */
    List<LineSnapshot> snapshotLines(Integer documentId);

    /**
     * Replaces the song's blocks with {@code lines}, restoring a snapshot taken
     * by {@link #snapshotLines}. Keeps at least one (empty) block.
     */
    void replaceLines(Integer documentId, List<LineSnapshot> lines);
}
