package com.scripty.service;

import com.scripty.dto.SongBlock;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.util.List;

/**
 * CRUD for the ordered lyric blocks that make up a song. Every structural change
 * rebuilds the parent {@link com.scripty.dto.TextDocument} content so existing
 * song features (insert-into-script, share, export) stay in sync.
 */
public interface SongBlockService {

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

    /** Deletes a block; keeps at least one (empty) block in the song. */
    Integer deleteBlock(Integer blockId);

    SongBlock moveUp(Integer blockId);

    SongBlock moveDown(Integer blockId);

    /**
     * Moves a block to {@code position} (zero-based index within the song),
     * clamped to the song's bounds. Backs drag-and-drop reordering.
     */
    SongBlock moveTo(Integer blockId, int position);
}
