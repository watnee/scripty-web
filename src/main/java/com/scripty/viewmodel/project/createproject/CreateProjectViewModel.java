/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.createproject;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;

/**
 *
 * @author chris
 */
public class CreateProjectViewModel {
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateProjectCommandModel createProjectCommandModel;

    public CreateProjectCommandModel getCreateProjectCommandModel() {
        return createProjectCommandModel;
    }

    public void setCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel) {
        this.createProjectCommandModel = createProjectCommandModel;
    }
    
}
