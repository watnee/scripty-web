package com.scripty.service;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.viewmodel.block.BlockViewModel;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;

public interface BlockService {

    Block read(Integer id);

    CreateBlockViewModel getCreateBlockViewModel(Integer projectId);
    CreateBlockBelowViewModel getCreateBlockBelowViewModel(Integer id);
    EditBlockViewModel getEditBlockViewModel(Integer id);
    BlockViewModel getBlockViewModel(Integer id);

    Block saveCreateBlockCommandModel(CreateBlockCommandModel createBlockCommandModel);
    Block saveCreateBlockBelowCommandModel(CreateBlockBelowCommandModel createBlockBelowCommandModel);
    java.util.List<Block> insertBlocksAfter(Integer afterBlockId, java.util.List<CreateBlockBelowCommandModel> blocks);
    /**
     * Replaces every contiguous run of blocks linked to {@code sourceDocumentId}
     * with the given lines (same type/source on each block).
     * @return true if any linked blocks were updated, inserted, or deleted
     */
    boolean replaceLinkedDocumentBlocks(Integer projectId, Integer sourceDocumentId,
                                        java.util.List<CreateBlockBelowCommandModel> blocks);
    Block saveEditBlockCommandModel(EditBlockCommandModel editBlockCommandModel);
    Block updateBlockTypeAndContent(Integer id, String type, String content, Integer personId, String tags);

    Block createInitialBlock(Integer projectId);
    Block updateSceneName(Integer id, String name);
    Block updateCharacterName(Integer id, String name);

    /**
     * Result of a find &amp; replace run: the blocks whose content changed and the
     * total number of occurrences replaced across them.
     */
    record FindReplaceResult(java.util.List<Block> updatedBlocks, int replacements) {
        public static FindReplaceResult empty() {
            return new FindReplaceResult(java.util.List.of(), 0);
        }
    }

    /**
     * Replaces occurrences of {@code find} in block content.
     * Scope is the whole project (or the given edition when {@code editionId} is set),
     * narrowed to a single block when {@code blockId} is set. When {@code occurrence}
     * is set alongside {@code blockId}, only that 1-based match within the block is
     * replaced (used by single "Replace" in the editor).
     */
    FindReplaceResult findReplaceInBlocks(Integer projectId, Integer editionId,
                                          String find, String replace,
                                          boolean matchCase, boolean wholeWord,
                                          Integer blockId, Integer occurrence);

    Block deleteBlock(Integer id);
    Block moveBlockUp(Integer id);
    Block moveBlockDown(Integer id);
    Block moveBlockTo(Integer id, int newOrder);
    Block toggleBookmark(Integer id);
    Block togglePinned(Integer id);
    void addTagsToBlocks(java.util.List<Integer> ids, String tags);
    void deleteBlocks(java.util.List<Integer> ids);
    void setBlockTypes(java.util.List<Integer> ids, String type);
    void setBlockAlignments(java.util.List<Integer> ids, String align);
    void toggleBlockTextStyles(java.util.List<Integer> ids, String style);
}
