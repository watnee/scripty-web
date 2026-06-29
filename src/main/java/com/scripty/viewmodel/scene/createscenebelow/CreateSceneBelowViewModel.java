/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.scene.createscenebelow;

import com.scripty.commandmodel.scene.createscenebelow.CreateSceneBelowCommandModel;

/**
 *
 * @author chris
 */
public class CreateSceneBelowViewModel {
    
    private int projectId;
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateSceneBelowCommandModel createSceneBelowCommandModel;

    public CreateSceneBelowCommandModel getCreateSceneBelowCommandModel() {
        return createSceneBelowCommandModel;
    }

    public void setCreateSceneBelowCommandModel(CreateSceneBelowCommandModel createSceneBelowCommandModel) {
        this.createSceneBelowCommandModel = createSceneBelowCommandModel;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }
    
}
