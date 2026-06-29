/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.commandmodel.block.createblockbelow.CreateBlockBelowCommandModel;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.viewmodel.block.createblock.CreateBlockViewModel;
import com.scripty.viewmodel.block.createblockbelow.CreateBlockBelowViewModel;
import com.scripty.viewmodel.block.editblock.EditBlockViewModel;
import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;

/**
 *
 * @author chris
 */
public interface BlockWebService {
    
    public CreateBlockViewModel getCreateBlockViewModel(Integer sceneId);
    public CreateBlockBelowViewModel getCreateBlockBelowViewModel(Integer id);
    public EditBlockViewModel getEditBlockViewModel(Integer id);
    public BlockViewModel getBlockViewModel(Integer id);

    public Block saveCreateBlockCommandModel(CreateBlockCommandModel createBlockCommandModel);
    public Block saveCreateBlockBelowCommandModel(CreateBlockBelowCommandModel createBlockBelowCommandModel);
    public Block saveEditBlockCommandModel(EditBlockCommandModel editBlockCommandModel);

    public Block deleteBlock(Integer id);
    public Block moveBlockUp(Integer id);
    public Block moveBlockDown(Integer id);
    public Block moveBlockTo(Integer id, int newOrder);
    
}
