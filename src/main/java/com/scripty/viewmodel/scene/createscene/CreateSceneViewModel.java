/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.scene.createscene;

import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;

/**
 *
 * @author chris
 */
public class CreateSceneViewModel {
    
    private int projectId;
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateSceneCommandModel createSceneCommandModel;

    public CreateSceneCommandModel getCreateSceneCommandModel() {
        return createSceneCommandModel;
    }

    public void setCreateSceneCommandModel(CreateSceneCommandModel createSceneCommandModel) {
        this.createSceneCommandModel = createSceneCommandModel;
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }
    
}
