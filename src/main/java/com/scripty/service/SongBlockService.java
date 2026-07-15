package com.scripty.service;

import com.scripty.dto.Block;
import java.util.List;

/**
 * Block-based editing for songs (and other {@code TextDocument}s). Song blocks are plain,
 * reorderable lyric lines that live in the shared {@code block} table with
 * {@code text_document_id} set (and no {@code script_edition_id}). This service is intentionally
 * isolated from {@link BlockService}, which is coupled to screenplay semantics
 * (script editions, characters, scene delimiters).
 *
 * <p>The song's {@code content} blob stays the source of truth for the rest of the app
 * (insert-into-screenplay, share-by-email, list preview, export). Every mutation recomputes
 * {@code content} as the newline-join of the song's blocks so those consumers keep working.
 */
public interface SongBlockService {

    /** The single block type used for song lines. */
    String SONG_BLOCK_TYPE = Block.TYPE_LYRICS;

    Block read(Integer blockId);

    /**
     * Returns the document's blocks, seeding them from the existing {@code content} on first use
     * (one block per line; a single empty block when the document is blank). Does not modify
     * {@code content}.
     */
    List<Block> ensureBlocks(Integer documentId);

    List<Block> listBlocks(Integer documentId);

    /** Inserts a new block immediately after {@code afterBlockId}. */
    Block createBelow(Integer afterBlockId, String content);

    /** Appends a new empty block at the end of the document. */
    Block createAtEnd(Integer documentId);

    Block editContent(Integer blockId, String content);

    /**
     * Deletes the block, or clears it in place when it is the document's only block so the editor
     * never ends up empty. Returns the affected block (for project/document resolution).
     */
    Block delete(Integer blockId);

    Block moveUp(Integer blockId);

    Block moveDown(Integer blockId);

    Block moveTo(Integer blockId, int newOrder);
}
