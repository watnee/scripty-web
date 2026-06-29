/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.project.editproject;

import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;

/**
 *
 * @author chris
 */
public class EditProjectViewModel {
    
    private int id;
    
    // Specifically to handle redisplaying when validation errors happen
    private EditProjectCommandModel editProjectCommandModel;

    public EditProjectCommandModel getEditProjectCommandModel() {
        return editProjectCommandModel;
    }

    public void setEditProjectCommandModel(EditProjectCommandModel editProjectCommandModel) {
        this.editProjectCommandModel = editProjectCommandModel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
    
}
