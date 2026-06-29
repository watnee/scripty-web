/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.block.editblock;

import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import java.util.List;

/**
 *
 * @author chris
 */
public class EditBlockViewModel {
    
    private int sceneId;
    private List<EditPersonViewModel> persons;
    
    // Specifically to handle redisplaying when validation errors happen
    private EditBlockCommandModel createBlockCommandModel;

    public EditBlockCommandModel getEditBlockCommandModel() {
        return createBlockCommandModel;
    }

    public void setEditBlockCommandModel(EditBlockCommandModel createBlockCommandModel) {
        this.createBlockCommandModel = createBlockCommandModel;
    }

    public int getSceneId() {
        return sceneId;
    }

    public void setSceneId(int sceneId) {
        this.sceneId = sceneId;
    }

    public List<EditPersonViewModel> getPersons() {
        return persons;
    }

    public void setPersons(List<EditPersonViewModel> persons) {
        this.persons = persons;
    }
    
}
