/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.createproject;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import java.util.List;

/**
 *
 * @author chris
 */
public class CreateProjectViewModel {
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateProjectCommandModel createProjectCommandModel;
    private List<String> teams;

    public CreateProjectCommandModel getCreateProjectCommandModel() {
        return createProjectCommandModel;
    }

    public void setCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel) {
        this.createProjectCommandModel = createProjectCommandModel;
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams;
    }
}
