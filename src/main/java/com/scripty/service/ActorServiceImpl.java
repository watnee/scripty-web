package com.scripty.service;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import com.scripty.viewmodel.actor.actorlist.CastingCharacterViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.actorprofile.AssignedRoleViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;
import com.scripty.viewmodel.person.personlist.CharacterViewModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ActorServiceImpl implements ActorService {

    private final ActorRepository actorRepository;
    private final PersonRepository personRepository;
    private final ProjectRepository projectRepository;

    @Autowired
    public ActorServiceImpl(ActorRepository actorRepository,
                            PersonRepository personRepository,
                            ProjectRepository projectRepository) {
        this.actorRepository = actorRepository;
        this.personRepository = personRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public ActorListViewModel getActorListViewModel(Integer projectId) {
        ActorListViewModel vm = new ActorListViewModel();
        List<Actor> actors = actorRepository.findAllByOrderByFirstNameAsc();
        Map<Integer, List<CharacterViewModel>> charactersByActor = new HashMap<>();
        List<CastingCharacterViewModel> projectCharacters = loadProjectCharacters(projectId, vm, charactersByActor);

        List<ActorViewModel> actorViewModels = new ArrayList<>();
        for (Actor actor : actors) {
            ActorViewModel avm = new ActorViewModel();
            avm.setId(actor.getId());
            avm.setFirst(actor.getFirstName());
            avm.setLast(actor.getLastName());
            avm.setPhone(actor.getPhone());
            avm.setEmail(actor.getEmail());
            avm.setAssignedCharacters(charactersByActor.getOrDefault(actor.getId(), List.of()));
            actorViewModels.add(avm);
        }
        vm.setActors(actorViewModels);
        vm.setCharacters(projectCharacters);
        return vm;
    }

    private List<CastingCharacterViewModel> loadProjectCharacters(Integer projectId,
                                                                  ActorListViewModel vm,
                                                                  Map<Integer, List<CharacterViewModel>> charactersByActor) {
        List<CastingCharacterViewModel> projectCharacters = new ArrayList<>();
        if (projectId == null) {
            return projectCharacters;
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return projectCharacters;
        }

        vm.setCharacterProjectTitle(project.getTitle());
        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(projectId);
        for (Person person : persons) {
            CastingCharacterViewModel castingCharacter = new CastingCharacterViewModel();
            castingCharacter.setId(person.getId());
            castingCharacter.setName(person.getName());

            if (person.getActor() != null) {
                Actor assignedActor = actorRepository.findById(person.getActor().getId()).orElse(null);
                if (assignedActor != null) {
                    castingCharacter.setActorId(assignedActor.getId());
                    castingCharacter.setActorName(formatActorName(assignedActor));

                    CharacterViewModel character = new CharacterViewModel();
                    character.setId(person.getId());
                    character.setName(person.getName());
                    charactersByActor
                            .computeIfAbsent(assignedActor.getId(), ignored -> new ArrayList<>())
                            .add(character);
                }
            }

            projectCharacters.add(castingCharacter);
        }
        return projectCharacters;
    }

    private String formatActorName(Actor actor) {
        if (actor.getLastName() == null || actor.getLastName().isBlank()) {
            return actor.getFirstName();
        }
        return actor.getFirstName() + " " + actor.getLastName();
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

        List<AssignedRoleViewModel> assignedRoles = new ArrayList<>();
        List<Person> persons = personRepository.findByActorIdOrderByNameAsc(id);
        for (Person person : persons) {
            AssignedRoleViewModel role = new AssignedRoleViewModel();
            role.setCharacterId(person.getId());
            role.setCharacterName(person.getName());
            if (person.getProject() != null) {
                Project project = projectRepository.findById(person.getProject().getId()).orElse(null);
                if (project != null) {
                    role.setProjectId(project.getId());
                    role.setProjectTitle(project.getTitle());
                }
            }
            assignedRoles.add(role);
        }
        vm.setAssignedRoles(assignedRoles);
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
