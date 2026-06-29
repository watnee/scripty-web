/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;

/**
 *
 * @author chris
 */
public interface ActorWebService {
    
    public ActorListViewModel getActorListViewModel();
    public ActorProfileViewModel getActorProfileViewModel(Integer id);

    public CreateActorViewModel getCreateActorViewModel();
    public EditActorViewModel getEditActorViewModel(Integer id);

    public Actor saveCreateActorCommandModel(CreateActorCommandModel createActorCommandModel);
    public Actor saveEditActorCommandModel(EditActorCommandModel editActorCommandModel);

    public Actor deleteActor(Integer id);
    
}
