/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.createactor;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;

/**
 *
 * @author chris
 */
public class CreateActorViewModel {
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateActorCommandModel createActorCommandModel;

    public CreateActorCommandModel getCreateActorCommandModel() {
        return createActorCommandModel;
    }

    public void setCreateActorCommandModel(CreateActorCommandModel createActorCommandModel) {
        this.createActorCommandModel = createActorCommandModel;
    }
    
}
