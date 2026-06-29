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

    public List<ActorViewModel> getActors() {
        return actors;
    }

    public void setActors(List<ActorViewModel> actors) {
        this.actors = actors;
    }
    
}
