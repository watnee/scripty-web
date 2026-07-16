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

    void setBlockFonts(java.util.List<Integer> ids, String font);

    /** Sets the background tint on the given blocks; an unknown or blank color clears it. */
    void setBlockHighlights(java.util.List<Integer> ids, String highlight);
    void toggleBlockTextStyles(java.util.List<Integer> ids, String style);
}
