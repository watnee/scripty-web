/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.createactor;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author chris
 */
public class CreateActorViewModel {
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateActorCommandModel createActorCommandModel;
    private List<ProjectViewModel> projects = new ArrayList<>();

    public CreateActorCommandModel getCreateActorCommandModel() {
        return createActorCommandModel;
    }

    public void setCreateActorCommandModel(CreateActorCommandModel createActorCommandModel) {
        this.createActorCommandModel = createActorCommandModel;
    }

    public List<ProjectViewModel> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectViewModel> projects) {
        this.projects = projects;
    }
    
}
