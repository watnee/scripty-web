/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.service.ActorService;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author chris
 */
public class ActorWebServiceImpl implements ActorWebService {
    
    ActorService actorService;

    @Inject
    public ActorWebServiceImpl(ActorService actorService) {
        this.actorService = actorService;
    }
    
    @Override
    public ActorListViewModel getActorListViewModel() {

        // Instantiate
        ActorListViewModel actorListViewModel = new ActorListViewModel();

        // Look stuff up
        List<Actor> actors = actorService.list();

        // Put stuff in
        actorListViewModel.setActors(translate(actors));

        return actorListViewModel;
    }

    @Override
    public ActorProfileViewModel getActorProfileViewModel(Integer id) {
        
        // Instantiate
        ActorProfileViewModel actorProfileViewModel = new ActorProfileViewModel();

        // Look up stuff
        Actor actor = actorService.read(id);

        // Put stuff
        actorProfileViewModel.setId(actor.getId());
        actorProfileViewModel.setFirst(actor.getFirstName());
        actorProfileViewModel.setLast(actor.getLastName());
        actorProfileViewModel.setPhone(actor.getPhone());
        actorProfileViewModel.setEmail(actor.getEmail());

        return actorProfileViewModel;
    }

    @Override
    public CreateActorViewModel getCreateActorViewModel() {

        // Instantiate
        CreateActorViewModel createActorViewModel = new CreateActorViewModel();

        CreateActorCommandModel commandModel = new CreateActorCommandModel();
        createActorViewModel.setCreateActorCommandModel(commandModel);

        return createActorViewModel;
    }
    
    @Override
    public EditActorViewModel getEditActorViewModel(Integer id) {

        // Instantiate
        EditActorViewModel editActorViewModel = new EditActorViewModel();

        // Look up stuff
        Actor existingActor = actorService.read(id);

        // Populate
        editActorViewModel.setId(id);

        // Populate commmand model
        EditActorCommandModel commandModel = new EditActorCommandModel();
        commandModel.setId(existingActor.getId());
        commandModel.setFirst(existingActor.getFirstName());
        commandModel.setLast(existingActor.getLastName());
        commandModel.setPhone(existingActor.getPhone());
        commandModel.setEmail(existingActor.getEmail());

        editActorViewModel.setEditActorCommandModel(commandModel);

        return editActorViewModel;
    }

    @Override
    public Actor saveCreateActorCommandModel(CreateActorCommandModel createActorCommandModel) {

        // Instantiate
        Actor actor = new Actor();
        
        // Put stuff
        actor.setFirstName(createActorCommandModel.getFirst());
        actor.setLastName(createActorCommandModel.getLast());
        actor.setPhone(createActorCommandModel.getPhone());
        actor.setEmail(createActorCommandModel.getEmail());

        // Save stuff
        actor = actorService.create(actor);
        
        return actor;
    }
    
    @Override
    public Actor saveEditActorCommandModel(EditActorCommandModel editActorCommandModel) {

        // Instantiate
        Actor actor = actorService.read(editActorCommandModel.getId());

        // Put stuff
        actor.setFirstName(editActorCommandModel.getFirst());
        actor.setLastName(editActorCommandModel.getLast());
        actor.setPhone(editActorCommandModel.getPhone());
        actor.setEmail(editActorCommandModel.getEmail());

        // Save stuff
        actorService.update(actor);

        return actor;
    }
    
    @Override
    public Actor deleteActor(Integer id) {

        // Instantiate
        Actor actor = actorService.read(id);

        // Delete
        actorService.delete(actor);

        return actor;
    }
    
    private List<ActorViewModel> translate(List<Actor> actors) {
        List<ActorViewModel> actorViewModels = new ArrayList<>();

        for (Actor actor : actors) {
            actorViewModels.add(translate(actor));
        }

        return actorViewModels;
    }

    private ActorViewModel translate(Actor actor) {

        ActorViewModel actorViewModel = new ActorViewModel();

        actorViewModel.setFirst(actor.getFirstName());
        actorViewModel.setLast(actor.getLastName());
        actorViewModel.setPhone(actor.getPhone());
        actorViewModel.setEmail(actor.getEmail());
        actorViewModel.setId(actor.getId());

        return actorViewModel;
    }
    
}
