/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.viewmodel.actor.actorlist;

import java.util.List;

/**
 *
 * @author chris
 */
public class ActorListViewModel {
    
    private List<ActorViewModel> actors;
    private Integer characterProjectId;
    private String characterProjectTitle;

    public List<ActorViewModel> getActors() {
        return actors;
    }

    public void setActors(List<ActorViewModel> actors) {
        this.actors = actors;
    }

    public Integer getCharacterProjectId() {
        return characterProjectId;
    }

    public void setCharacterProjectId(Integer characterProjectId) {
        this.characterProjectId = characterProjectId;
    }

    public String getCharacterProjectTitle() {
        return characterProjectTitle;
    }

    public void setCharacterProjectTitle(String characterProjectTitle) {
        this.characterProjectTitle = characterProjectTitle;
    }

}
