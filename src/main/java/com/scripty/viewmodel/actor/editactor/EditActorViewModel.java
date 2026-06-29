/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.editactor;

import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;

/**
 *
 * @author chris
 */
public class EditActorViewModel {
    
    private int id;
    
    // Specifically to handle redisplaying when validation errors happen
    private EditActorCommandModel editActorCommandModel;

    public EditActorCommandModel getEditActorCommandModel() {
        return editActorCommandModel;
    }

    public void setEditActorCommandModel(EditActorCommandModel editActorCommandModel) {
        this.editActorCommandModel = editActorCommandModel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
    
}
