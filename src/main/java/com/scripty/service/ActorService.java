package com.scripty.service;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;

public interface ActorService {

    ActorListViewModel getActorListViewModel();
    ActorProfileViewModel getActorProfileViewModel(Integer id);

    CreateActorViewModel getCreateActorViewModel();
    EditActorViewModel getEditActorViewModel(Integer id);

    Actor saveCreateActorCommandModel(CreateActorCommandModel createActorCommandModel);
    Actor saveEditActorCommandModel(EditActorCommandModel editActorCommandModel);

    Actor deleteActor(Integer id);
}
