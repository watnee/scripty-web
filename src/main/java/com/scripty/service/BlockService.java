package com.scripty.service;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;

public interface BlockService {

    Block read(Integer id);

    CreateBlockViewModel getCreateBlockViewModel(Integer sceneId);
    CreateBlockBelowViewModel getCreateBlockBelowViewModel(Integer id);
    EditBlockViewModel getEditBlockViewModel(Integer id);
    BlockViewModel getBlockViewModel(Integer id);

    Block saveCreateBlockCommandModel(CreateBlockCommandModel createBlockCommandModel);
    Block saveCreateBlockBelowCommandModel(CreateBlockBelowCommandModel createBlockBelowCommandModel);
    Block saveEditBlockCommandModel(EditBlockCommandModel editBlockCommandModel);

    Block deleteBlock(Integer id);
    Block moveBlockUp(Integer id);
    Block moveBlockDown(Integer id);
    Block moveBlockTo(Integer id, int newOrder);
    Block toggleBookmark(Integer id);
}
