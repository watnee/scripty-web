package com.scripty.service;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.repository.ActorRepository;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ActorServiceImpl implements ActorService {

    private final ActorRepository actorRepository;

    @Autowired
    public ActorServiceImpl(ActorRepository actorRepository) {
        this.actorRepository = actorRepository;
    }

    @Override
    public ActorListViewModel getActorListViewModel() {
        ActorListViewModel vm = new ActorListViewModel();
        List<Actor> actors = actorRepository.findAllByOrderByFirstNameAsc();
        List<ActorViewModel> actorViewModels = new ArrayList<>();
        for (Actor actor : actors) {
            ActorViewModel avm = new ActorViewModel();
            avm.setId(actor.getId());
            avm.setFirst(actor.getFirstName());
            avm.setLast(actor.getLastName());
            avm.setPhone(actor.getPhone());
            avm.setEmail(actor.getEmail());
            actorViewModels.add(avm);
        }
        vm.setActors(actorViewModels);
        return vm;
    }

    @Override
    public ActorProfileViewModel getActorProfileViewModel(Integer id) {
        ActorProfileViewModel vm = new ActorProfileViewModel();
        Actor actor = actorRepository.findById(id).orElse(null);
        vm.setId(actor.getId());
        vm.setFirst(actor.getFirstName());
        vm.setLast(actor.getLastName());
        vm.setPhone(actor.getPhone());
        vm.setEmail(actor.getEmail());
        return vm;
    }

    @Override
    public CreateActorViewModel getCreateActorViewModel() {
        CreateActorViewModel vm = new CreateActorViewModel();
        vm.setCreateActorCommandModel(new CreateActorCommandModel());
        return vm;
    }

    @Override
    public EditActorViewModel getEditActorViewModel(Integer id) {
        EditActorViewModel vm = new EditActorViewModel();
        Actor actor = actorRepository.findById(id).orElse(null);
        vm.setId(id);
        EditActorCommandModel commandModel = new EditActorCommandModel();
        commandModel.setId(actor.getId());
        commandModel.setFirst(actor.getFirstName());
        commandModel.setLast(actor.getLastName());
        commandModel.setPhone(actor.getPhone());
        commandModel.setEmail(actor.getEmail());
        vm.setEditActorCommandModel(commandModel);
        return vm;
    }

    @Override
    public Actor saveCreateActorCommandModel(CreateActorCommandModel cmd) {
        Actor actor = new Actor();
        actor.setFirstName(cmd.getFirst());
        actor.setLastName(cmd.getLast());
        actor.setPhone(cmd.getPhone());
        actor.setEmail(cmd.getEmail());
        return actorRepository.save(actor);
    }

    @Override
    public Actor saveEditActorCommandModel(EditActorCommandModel cmd) {
        Actor actor = actorRepository.findById(cmd.getId()).orElse(null);
        actor.setFirstName(cmd.getFirst());
        actor.setLastName(cmd.getLast());
        actor.setPhone(cmd.getPhone());
        actor.setEmail(cmd.getEmail());
        actorRepository.save(actor);
        return actor;
    }

    @Override
    public Actor deleteActor(Integer id) {
        Actor actor = actorRepository.findById(id).orElse(null);
        actorRepository.delete(actor);
        return actor;
    }
}
