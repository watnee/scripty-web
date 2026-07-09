package com.scripty.service;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.dto.Audition;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.AuditionRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import com.scripty.viewmodel.actor.actorlist.AuditionActorViewModel;
import com.scripty.viewmodel.actor.actorlist.CastingCharacterViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.actorprofile.AssignedRoleViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;
import com.scripty.viewmodel.person.personlist.CharacterViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActorServiceImpl implements ActorService {

    private final ActorRepository actorRepository;
    private final PersonRepository personRepository;
    private final ProjectRepository projectRepository;
    private final AuditionRepository auditionRepository;
    private final ActorHeadshotService actorHeadshotService;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public ActorServiceImpl(ActorRepository actorRepository,
                            PersonRepository personRepository,
                            ProjectRepository projectRepository,
                            AuditionRepository auditionRepository,
                            ActorHeadshotService actorHeadshotService,
                            ProjectActivityService projectActivityService) {
        this.actorRepository = actorRepository;
        this.personRepository = personRepository;
        this.projectRepository = projectRepository;
        this.auditionRepository = auditionRepository;
        this.actorHeadshotService = actorHeadshotService;
        this.projectActivityService = projectActivityService;
    }

    @Override
    public ActorListViewModel getActorListViewModel(Integer projectId) {
        ActorListViewModel vm = new ActorListViewModel();
        Map<Integer, List<CharacterViewModel>> charactersByActor = new HashMap<>();
        List<CastingCharacterViewModel> projectCharacters = loadProjectCharacters(projectId, vm, charactersByActor);

        List<Actor> actors = projectId != null
                ? actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(projectId)
                : List.of();
        List<ActorViewModel> actorViewModels = new ArrayList<>();
        for (Actor actor : actors) {
            ActorViewModel avm = new ActorViewModel();
            avm.setId(actor.getId());
            avm.setFirst(actor.getFirstName());
            avm.setLast(actor.getLastName());
            avm.setPhone(actor.getPhone());
            avm.setEmail(actor.getEmail());
            avm.setHasHeadshot(actorHeadshotService.hasHeadshot(actor));
            avm.setAssignedCharacters(charactersByActor.getOrDefault(actor.getId(), List.of()));
            actorViewModels.add(avm);
        }
        vm.setActors(actorViewModels);
        vm.setCharacters(projectCharacters);
        if (projectId != null) {
            applyAuditions(projectId, actorViewModels, projectCharacters);
        }
        return vm;
    }

    private void applyAuditions(Integer projectId,
                                List<ActorViewModel> actorViewModels,
                                List<CastingCharacterViewModel> projectCharacters) {
        Map<Integer, ActorViewModel> actorsById = new HashMap<>();
        for (ActorViewModel actor : actorViewModels) {
            actorsById.put(actor.getId(), actor);
        }

        Map<Integer, CastingCharacterViewModel> charactersById = new HashMap<>();
        for (CastingCharacterViewModel character : projectCharacters) {
            charactersById.put(character.getId(), character);
        }

        List<Audition> auditions = auditionRepository.findByProjectId(projectId);
        for (Audition audition : auditions) {
            Actor actor = audition.getActor();
            Person person = audition.getPerson();
            if (actor == null || person == null) {
                continue;
            }

            ActorViewModel actorViewModel = actorsById.get(actor.getId());
            CastingCharacterViewModel characterViewModel = charactersById.get(person.getId());
            if (actorViewModel == null || characterViewModel == null) {
                continue;
            }

            CharacterViewModel auditionCharacter = new CharacterViewModel();
            auditionCharacter.setId(person.getId());
            auditionCharacter.setName(person.getName());
            actorViewModel.getAuditionCharacters().add(auditionCharacter);
            actorViewModel.getAuditionCharacterIds().add(person.getId());

            AuditionActorViewModel auditionActor = new AuditionActorViewModel();
            auditionActor.setId(actor.getId());
            auditionActor.setName(formatActorName(actor));
            characterViewModel.getAuditionActors().add(auditionActor);
        }
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
        Actor actor = actorRepository.findByIdWithProjects(id).orElse(null);
        vm.setId(actor.getId());
        vm.setFirst(actor.getFirstName());
        vm.setLast(actor.getLastName());
        vm.setPhone(actor.getPhone());
        vm.setEmail(actor.getEmail());
        vm.setHasHeadshot(actorHeadshotService.hasHeadshot(actor));
        vm.setProjects(toProjectViewModels(actor.getProjects()));

        Set<Integer> actorProjectIds = projectIds(actor);
        List<AssignedRoleViewModel> assignedRoles = new ArrayList<>();
        List<AssignedRoleViewModel> auditionRoles = new ArrayList<>();
        List<Person> persons = personRepository.findByActorIdOrderByNameAsc(id);
        for (Person person : persons) {
            if (person.getProject() == null || !actorProjectIds.contains(person.getProject().getId())) {
                continue;
            }
            if (person.getActor() != null && person.getActor().getId().equals(id)) {
                AssignedRoleViewModel role = new AssignedRoleViewModel();
                role.setCharacterId(person.getId());
                role.setCharacterName(person.getName());
                Project project = projectRepository.findById(person.getProject().getId()).orElse(null);
                if (project != null) {
                    role.setProjectId(project.getId());
                    role.setProjectTitle(project.getTitle());
                }
                assignedRoles.add(role);
            }
        }

        List<Audition> auditions = auditionRepository.findByActorId(id);
        for (Audition audition : auditions) {
            Person person = audition.getPerson();
            if (person == null || person.getProject() == null
                    || !actorProjectIds.contains(person.getProject().getId())) {
                continue;
            }
            if (person.getActor() != null && person.getActor().getId().equals(id)) {
                continue;
            }
            AssignedRoleViewModel role = new AssignedRoleViewModel();
            role.setCharacterId(person.getId());
            role.setCharacterName(person.getName());
            Project project = projectRepository.findById(person.getProject().getId()).orElse(null);
            if (project != null) {
                role.setProjectId(project.getId());
                role.setProjectTitle(project.getTitle());
            }
            auditionRoles.add(role);
        }

        vm.setAssignedRoles(assignedRoles);
        vm.setAuditionRoles(auditionRoles);
        return vm;
    }

    @Override
    public CreateActorViewModel getCreateActorViewModel(Integer projectId) {
        CreateActorViewModel vm = new CreateActorViewModel();
        CreateActorCommandModel commandModel = new CreateActorCommandModel();
        if (projectId != null) {
            commandModel.setProjectIds(List.of(projectId));
        }
        vm.setCreateActorCommandModel(commandModel);
        return vm;
    }

    @Override
    public EditActorViewModel getEditActorViewModel(Integer id) {
        EditActorViewModel vm = new EditActorViewModel();
        Actor actor = actorRepository.findByIdWithProjects(id).orElse(null);
        vm.setId(id);
        vm.setHasHeadshot(actorHeadshotService.hasHeadshot(actor));
        EditActorCommandModel commandModel = new EditActorCommandModel();
        commandModel.setId(actor.getId());
        commandModel.setFirst(actor.getFirstName());
        commandModel.setLast(actor.getLastName());
        commandModel.setPhone(actor.getPhone());
        commandModel.setEmail(actor.getEmail());
        commandModel.setProjectIds(actor.getProjects().stream().map(Project::getId).toList());
        vm.setEditActorCommandModel(commandModel);

        Set<Integer> actorProjectIds = projectIds(actor);
        List<AssignedRoleViewModel> assignedRoles = new ArrayList<>();
        List<Person> persons = personRepository.findByActorIdOrderByNameAsc(id);
        for (Person person : persons) {
            if (person.getProject() == null || !actorProjectIds.contains(person.getProject().getId())) {
                continue;
            }
            AssignedRoleViewModel role = new AssignedRoleViewModel();
            role.setCharacterId(person.getId());
            role.setCharacterName(person.getName());
            assignedRoles.add(role);
        }
        vm.setAssignedRoles(assignedRoles);
        return vm;
    }

    @Override
    public Actor saveCreateActorCommandModel(CreateActorCommandModel cmd) {
        Actor actor = new Actor();
        actor.setFirstName(PlainTextSanitizer.sanitizeSingleLine(cmd.getFirst()));
        actor.setLastName(PlainTextSanitizer.sanitizeSingleLine(cmd.getLast()));
        actor.setPhone(PlainTextSanitizer.sanitizeSingleLine(cmd.getPhone()));
        actor.setEmail(PlainTextSanitizer.sanitizeSingleLine(cmd.getEmail()));
        actor.setProjects(resolveProjects(cmd.getProjectIds()));
        Actor saved = actorRepository.save(actor);
        String name = formatActorName(saved);
        for (Project project : saved.getProjects()) {
            projectActivityService.recordForCurrentUser(
                    project.getId(),
                    ProjectActivity.ACTION_ACTOR_ADDED,
                    "added actor " + name + " to casting",
                    ProjectActivity.ENTITY_ACTOR,
                    saved.getId());
        }
        return saved;
    }

    @Override
    public Actor saveEditActorCommandModel(EditActorCommandModel cmd) {
        Actor actor = actorRepository.findById(cmd.getId()).orElse(null);
        Set<Integer> previousProjectIds = projectIds(actor);
        actor.setFirstName(PlainTextSanitizer.sanitizeSingleLine(cmd.getFirst()));
        actor.setLastName(PlainTextSanitizer.sanitizeSingleLine(cmd.getLast()));
        actor.setPhone(PlainTextSanitizer.sanitizeSingleLine(cmd.getPhone()));
        actor.setEmail(PlainTextSanitizer.sanitizeSingleLine(cmd.getEmail()));
        actor.setProjects(resolveProjects(cmd.getProjectIds()));
        actorRepository.save(actor);

        String name = formatActorName(actor);
        Set<Integer> currentProjectIds = projectIds(actor);
        for (Integer projectId : currentProjectIds) {
            if (!previousProjectIds.contains(projectId)) {
                projectActivityService.recordForCurrentUser(
                        projectId,
                        ProjectActivity.ACTION_ACTOR_ADDED,
                        "added actor " + name + " to casting",
                        ProjectActivity.ENTITY_ACTOR,
                        actor.getId());
            } else {
                projectActivityService.recordForCurrentUser(
                        projectId,
                        ProjectActivity.ACTION_ACTOR_UPDATED,
                        "updated actor " + name,
                        ProjectActivity.ENTITY_ACTOR,
                        actor.getId());
            }
        }
        for (Integer projectId : previousProjectIds) {
            if (!currentProjectIds.contains(projectId)) {
                projectActivityService.recordForCurrentUser(
                        projectId,
                        ProjectActivity.ACTION_ACTOR_REMOVED,
                        "removed actor " + name + " from casting",
                        ProjectActivity.ENTITY_ACTOR,
                        actor.getId());
            }
        }
        return actor;
    }

    @Override
    public Actor deleteActor(Integer id) {
        Actor actor = actorRepository.findByIdWithProjects(id).orElse(null);
        String name = formatActorName(actor);
        Set<Integer> linkedProjectIds = projectIds(actor);
        actorHeadshotService.deleteHeadshot(actor);
        actorRepository.delete(actor);
        for (Integer projectId : linkedProjectIds) {
            projectActivityService.recordForCurrentUser(
                    projectId,
                    ProjectActivity.ACTION_ACTOR_REMOVED,
                    "removed actor " + name + " from casting",
                    ProjectActivity.ENTITY_ACTOR,
                    id);
        }
        return actor;
    }

    private List<Project> resolveProjects(List<Integer> projectIds) {
        List<Project> projects = new ArrayList<>();
        if (projectIds == null) {
            return projects;
        }
        for (Integer projectId : new HashSet<>(projectIds)) {
            projectRepository.findById(projectId).ifPresent(projects::add);
        }
        return projects;
    }

    private Set<Integer> projectIds(Actor actor) {
        Set<Integer> ids = new HashSet<>();
        for (Project project : actor.getProjects()) {
            ids.add(project.getId());
        }
        return ids;
    }

    private List<ProjectViewModel> toProjectViewModels(List<Project> projects) {
        List<ProjectViewModel> viewModels = new ArrayList<>();
        for (Project project : projects) {
            ProjectViewModel pvm = new ProjectViewModel();
            pvm.setId(project.getId());
            pvm.setTitle(project.getTitle());
            viewModels.add(pvm);
        }
        return viewModels;
    }
}
