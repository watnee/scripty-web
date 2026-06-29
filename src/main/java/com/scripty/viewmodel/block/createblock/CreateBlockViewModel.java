/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.block.createblock;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import java.util.List;

/**
 *
 * @author chris
 */
public class CreateBlockViewModel {
    
    private int sceneId;
    private List<CreatePersonViewModel> persons;
    
    // Specifically to handle redisplaying when validation errors happen
    private CreateBlockCommandModel createBlockCommandModel;

    public CreateBlockCommandModel getCreateBlockCommandModel() {
        return createBlockCommandModel;
    }

    public void setCreateBlockCommandModel(CreateBlockCommandModel createBlockCommandModel) {
        this.createBlockCommandModel = createBlockCommandModel;
    }

    public int getSceneId() {
        return sceneId;
    }

    public void setSceneId(int sceneId) {
        this.sceneId = sceneId;
    }

    public List<CreatePersonViewModel> getPersons() {
        return persons;
    }

    public void setPersons(List<CreatePersonViewModel> persons) {
        this.persons = persons;
    }
    
}
