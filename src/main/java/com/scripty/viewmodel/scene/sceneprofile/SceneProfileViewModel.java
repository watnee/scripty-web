/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.scene.sceneprofile;

import java.util.List;

/**
 *
 * @author chris
 */
public class SceneProfileViewModel {
    
    private int id;
    private String name;
    private int projectId;
    private String projectTitle;
    private int previousSceneId;
    private String previousSceneName;
    private int nextSceneId;
    private String nextSceneName;
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

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public void setProjectTitle(String projectTitle) {
        this.projectTitle = projectTitle;
    }

    public int getPreviousSceneId() {
        return previousSceneId;
    }

    public void setPreviousSceneId(int previousSceneId) {
        this.previousSceneId = previousSceneId;
    }

    public String getPreviousSceneName() {
        return previousSceneName;
    }

    public void setPreviousSceneName(String previousSceneName) {
        this.previousSceneName = previousSceneName;
    }

    public int getNextSceneId() {
        return nextSceneId;
    }

    public void setNextSceneId(int nextSceneId) {
        this.nextSceneId = nextSceneId;
    }

    public String getNextSceneName() {
        return nextSceneName;
    }

    public void setNextSceneName(String nextSceneName) {
        this.nextSceneName = nextSceneName;
    }

    public List<BlockViewModel> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<BlockViewModel> blocks) {
        this.blocks = blocks;
    }
    
}
