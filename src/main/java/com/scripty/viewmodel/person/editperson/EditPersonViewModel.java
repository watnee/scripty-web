/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.person.editperson;

import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import java.util.List;

/**
 *
 * @author chris
 */
public class EditPersonViewModel {
    
    private int id;
    private List<EditActorViewModel> actors;
    
    // Specifically to handle redisplaying when validation errors happen
    private EditPersonCommandModel createPersonCommandModel;

    public EditPersonCommandModel getEditPersonCommandModel() {
        return createPersonCommandModel;
    }

    public void setEditPersonCommandModel(EditPersonCommandModel createPersonCommandModel) {
        this.createPersonCommandModel = createPersonCommandModel;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<EditActorViewModel> getActors() {
        return actors;
    }

    public void setActors(List<EditActorViewModel> actors) {
        this.actors = actors;
    }
    
}
