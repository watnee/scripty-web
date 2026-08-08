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

    /** Song version (edition) id owning the block, or null if not found. */
    Integer editionIdForBlock(Integer blockId);

    /**
     * Returns the given song version's blocks, seeding them from the document's
     * free-text content on first access when the version is published (or a
     * single empty block otherwise).
     */
    List<SongBlockViewModel> getBlocks(Integer documentId, Integer editionId);

    /** Appends a new empty block at the end of the given song version. */
    SongBlock appendBlock(Integer documentId, Integer editionId);

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
     * Replaces a single occurrence of {@code find} inside one lyric line — the
     * one-at-a-time "Replace" that walks a find down a song.
     *
     * <p>{@code occurrence} is the zero-based index of the match to swap within
     * the line. Answers null when there is nothing at that index, so the caller
     * can tell a no-op from a rewrite.
     */
    SongBlock replaceOccurrenceInBlock(Integer blockId, String find, String replace,
                                       boolean matchCase, boolean wholeWord, int occurrence);

    /**
     * Replaces every occurrence of {@code find} across a version's lines, and
     * answers how many lines changed.
     *
     * <p>A null {@code ids} means every live line in the version. A supplied
     * list is intersected with that same set, so ids belonging to another song
     * are ignored rather than trusted.
     */
    int replaceInLines(Integer documentId, Integer editionId, List<Integer> ids,
                       String find, String replace, boolean matchCase, boolean wholeWord);

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
     * The song version's lines in order, as an undo/redo snapshot. Null when the
     * document or version does not exist.
     */
    List<LineSnapshot> snapshotLines(Integer documentId, Integer editionId);

    /**
     * Replaces the song version's blocks with {@code lines}, restoring a snapshot
     * taken by {@link #snapshotLines}. Keeps at least one (empty) block.
     */
    void replaceLines(Integer documentId, Integer editionId, List<LineSnapshot> lines);

    /**
     * Re-splits a song's default version into lines from free text, in place.
     *
     * For the one caller that arrives with the text and not the lines: reading a
     * project archive back into a song that already exists. Seeding only ever
     * runs on a song with no lines at all, so without this the lyric on screen
     * would go on showing what it said before the file arrived while the text
     * underneath it said something else.
     *
     * The tints go, because free text carries none — which is what an archive
     * has always meant by a song.
     */
    void replaceLinesFromContent(Integer documentId, String content);
}
