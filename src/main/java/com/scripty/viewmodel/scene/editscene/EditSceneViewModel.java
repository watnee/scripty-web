/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.scene.editscene;

import com.scripty.commandmodel.scene.editscene.EditSceneCommandModel;

/**
 *
 * @author chris
 */
public class EditSceneViewModel {
    
    private int id;
    
    // Specifically to handle redisplaying when validation errors happen
    private EditSceneCommandModel editSceneCommandModel;

    public EditSceneCommandModel getEditSceneCommandModel() {
        return editSceneCommandModel;
    }

    public void setEditSceneCommandModel(EditSceneCommandModel editSceneCommandModel) {
        this.editSceneCommandModel = editSceneCommandModel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
    
}
