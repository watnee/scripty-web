/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.projectprofile;

import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;
import java.util.List;

/**
 *
 * @author chris
 */
public class SceneViewModel {

    private int id;
    private String name;
    private List<BlockViewModel> blocks;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<BlockViewModel> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockViewModel> blocks) {
        this.blocks = blocks;
    }

}
