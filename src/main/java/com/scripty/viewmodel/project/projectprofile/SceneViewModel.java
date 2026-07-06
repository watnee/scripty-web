package com.scripty.viewmodel.project.projectprofile;

import com.scripty.viewmodel.block.BlockViewModel;
import java.util.List;

/**
 * A group of blocks on the project page, headed by a scene-type block.
 * The id is the scene block's id (null when blocks precede any scene block).
 */
public class SceneViewModel {

    private Integer id;
    private String name;
    private List<BlockViewModel> blocks;
    private Integer lastBlockId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public Integer getLastBlockId() {
        return lastBlockId;
    }

    public void setLastBlockId(Integer lastBlockId) {
        this.lastBlockId = lastBlockId;
    }
}
