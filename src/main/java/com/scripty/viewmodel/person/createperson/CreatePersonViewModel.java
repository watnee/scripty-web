/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.person.createperson;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import java.util.List;

/**
 *
 * @author chris
 */
public class CreatePersonViewModel {
    
    private int projectId;
    private List<CreateActorViewModel> actors;
    
    // Specifically to handle redisplaying when validation errors happen
    private CreatePersonCommandModel createPersonCommandModel;

    public CreatePersonCommandModel getCreatePersonCommandModel() {
        return createPersonCommandModel;
    }

    public void setCreatePersonCommandModel(CreatePersonCommandModel createPersonCommandModel) {
        this.createPersonCommandModel = createPersonCommandModel;
    }
    
    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public List<CreateActorViewModel> getActors() {
        return actors;
    }

    public void setActors(List<CreateActorViewModel> actors) {
        this.actors = actors;
    }
    
}
