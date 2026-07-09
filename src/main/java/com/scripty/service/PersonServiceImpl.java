package com.scripty.service;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Actor;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.person.createperson.CreateActorViewModel;
import com.scripty.viewmodel.person.createperson.CreatePersonViewModel;
import com.scripty.viewmodel.person.editperson.EditActorViewModel;
import com.scripty.viewmodel.person.editperson.EditPersonViewModel;
import com.scripty.viewmodel.person.personlist.CharacterViewModel;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PersonServiceImpl implements PersonService {

    private final PersonRepository personRepository;
    private final ActorRepository actorRepository;
    private final ProjectRepository projectRepository;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public PersonServiceImpl(PersonRepository personRepository,
                             ActorRepository actorRepository,
                             ProjectRepository projectRepository,
                             ProjectActivityService projectActivityService) {
        this.personRepository = personRepository;
        this.actorRepository = actorRepository;
        this.projectRepository = projectRepository;
        this.projectActivityService = projectActivityService;
    }

    @Override
    public Person create(Person person) {
        Person saved = personRepository.save(person);
        if (saved.getProject() != null) {
            updateProjectLastEdited(saved.getProject().getId());
            projectActivityService.recordForCurrentUser(
                    saved.getProject().getId(),
                    ProjectActivity.ACTION_CHARACTER_CREATED,
                    "added character \"" + saved.getName() + "\"",
                    ProjectActivity.ENTITY_PERSON,
                    saved.getId());
        }
        return saved;
    }

    @Override
    public Person read(Integer id) {
        return personRepository.findById(id).orElse(null);
    }

    @Override
    public List<Person> getPersonsByProject(Project project) {
        return personRepository.findByProjectIdOrderByNameAsc(project.getId());
    }

    @Override
    public PersonListViewModel getPersonListViewModel(Integer projectId) {
        PersonListViewModel vm = new PersonListViewModel();
        Project project = projectRepository.findById(projectId).orElse(null);
        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(projectId);

        vm.setProjectId(project.getId());
        vm.setProjectTitle(project.getTitle());

        List<CharacterViewModel> characterViewModels = new ArrayList<>();
        for (Person person : persons) {
            CharacterViewModel cvm = new CharacterViewModel();
            cvm.setId(person.getId());
            cvm.setName(person.getName());
            characterViewModels.add(cvm);
        }
        vm.setCharacters(characterViewModels);
        return vm;
    }

    @Override
    public PersonProfileViewModel getPersonProfileViewModel(Integer id) {
        PersonProfileViewModel vm = new PersonProfileViewModel();
        Person person = personRepository.findById(id).orElse(null);

        vm.setId(person.getId());
        vm.setName(person.getName());
        vm.setFullName(person.getFullName());

        if (person.getActor() != null) {
            Actor actor = actorRepository.findById(person.getActor().getId()).orElse(null);
            if (actor != null) {
                vm.setActorId(actor.getId());
                vm.setActorName(actor.getFirstName() + " " + actor.getLastName());
            }
        }

        if (person.getProject() != null) {
            Project project = projectRepository.findById(person.getProject().getId()).orElse(null);
            if (project != null) {
                vm.setProjectId(project.getId());
                vm.setProjectTitle(project.getTitle());
            }
        }

        return vm;
    }

    @Override
    public CreatePersonViewModel getCreatePersonViewModel(Integer projectId) {
        CreatePersonViewModel vm = new CreatePersonViewModel();
        CreatePersonCommandModel commandModel = new CreatePersonCommandModel();
        commandModel.setProjectId(projectId);
        vm.setCreatePersonCommandModel(commandModel);
        vm.setProjectId(projectId);

        List<Actor> actors = actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(projectId);
        List<CreateActorViewModel> actorViewModels = new ArrayList<>();
        for (Actor actor : actors) {
            CreateActorViewModel avm = new CreateActorViewModel();
            avm.setId(actor.getId());
            avm.setName(actor.getFirstName() + " " + actor.getLastName());
            actorViewModels.add(avm);
        }
        vm.setActors(actorViewModels);
        return vm;
    }

    @Override
    public EditPersonViewModel getEditPersonViewModel(Integer id) {
        EditPersonViewModel vm = new EditPersonViewModel();
        Person person = personRepository.findById(id).orElse(null);
        Project project = projectRepository.findById(person.getProject().getId()).orElse(null);
        List<Actor> allActors = actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(project.getId());

        List<EditActorViewModel> actorViewModels = new ArrayList<>();
        for (Actor actor : allActors) {
            EditActorViewModel avm = new EditActorViewModel();
            avm.setId(actor.getId());
            avm.setName(actor.getFirstName() + " " + actor.getLastName());
            actorViewModels.add(avm);
        }
        vm.setActors(actorViewModels);
        vm.setId(id);

        EditPersonCommandModel commandModel = new EditPersonCommandModel();
        commandModel.setId(person.getId());
        commandModel.setName(person.getName());
        commandModel.setFullName(person.getFullName());
        if (person.getActor() != null) {
            commandModel.setActorId(person.getActor().getId());
        }
        commandModel.setProjectId(project.getId());
        vm.setEditPersonCommandModel(commandModel);
        return vm;
    }

    @Override
    public Person saveCreatePersonCommandModel(CreatePersonCommandModel cmd) {
        Person person = new Person();
        Actor actor = cmd.getActorId() != null
                ? actorRepository.findByIdWithProjects(cmd.getActorId()).orElse(null)
                : null;
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);

        person.setName(PlainTextSanitizer.sanitizeSingleLine(cmd.getName()));
        person.setFullName(PlainTextSanitizer.sanitizeSingleLine(cmd.getFullName()));
        if (actor != null && actorBelongsToProject(actor, project)) {
            person.setActor(actor);
        }
        if (project != null) person.setProject(project);
        Person saved = personRepository.save(person);
        if (project != null) {
            updateProjectLastEdited(project.getId());
            projectActivityService.recordForCurrentUser(
                    project.getId(),
                    ProjectActivity.ACTION_CHARACTER_CREATED,
                    "added character \"" + saved.getName() + "\"",
                    ProjectActivity.ENTITY_PERSON,
                    saved.getId());
        }
        return saved;
    }

    @Override
    public Person saveEditPersonCommandModel(EditPersonCommandModel cmd) {
        Person person = personRepository.findById(cmd.getId()).orElse(null);
        Actor actor = cmd.getActorId() != null
                ? actorRepository.findByIdWithProjects(cmd.getActorId()).orElse(null)
                : null;
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);

        person.setName(PlainTextSanitizer.sanitizeSingleLine(cmd.getName()));
        person.setFullName(PlainTextSanitizer.sanitizeSingleLine(cmd.getFullName()));
        Actor previousActor = person.getActor();
        person.setActor(actor != null && actorBelongsToProject(actor, project) ? actor : null);
        person.setProject(project);
        personRepository.save(person);
        if (project != null) {
            updateProjectLastEdited(project.getId());
            Integer previousActorId = previousActor != null ? previousActor.getId() : null;
            Integer newActorId = person.getActor() != null ? person.getActor().getId() : null;
            if (previousActorId == null ? newActorId != null : !previousActorId.equals(newActorId)) {
                if (person.getActor() != null) {
                    projectActivityService.recordForCurrentUser(
                            project.getId(),
                            ProjectActivity.ACTION_ACTOR_ASSIGNED,
                            "assigned " + actorDisplayName(person.getActor()) + " to \"" + person.getName() + "\"",
                            ProjectActivity.ENTITY_PERSON,
                            person.getId());
                }
            }
        }
        return person;
    }

    @Override
    public Person assignActorToCharacter(Integer characterId, Integer actorId) {
        Person person = personRepository.findById(characterId).orElse(null);
        Project project = person.getProject() != null
                ? projectRepository.findById(person.getProject().getId()).orElse(null)
                : null;

        if (actorId == null) {
            person.setActor(null);
        } else {
            Actor actor = actorRepository.findByIdWithProjects(actorId).orElse(null);
            person.setActor(actor != null && actorBelongsToProject(actor, project) ? actor : null);
        }

        personRepository.save(person);
        if (project != null) {
            updateProjectLastEdited(project.getId());
            if (person.getActor() != null) {
                projectActivityService.recordForCurrentUser(
                        project.getId(),
                        ProjectActivity.ACTION_ACTOR_ASSIGNED,
                        "assigned " + actorDisplayName(person.getActor()) + " to \"" + person.getName() + "\"",
                        ProjectActivity.ENTITY_PERSON,
                        person.getId());
            }
        }
        return person;
    }

    @Override
    public Person deletePerson(Integer id) {
        Person person = personRepository.findById(id).orElse(null);
        if (person != null) {
            Integer projectId = person.getProject() != null ? person.getProject().getId() : null;
            String name = person.getName();
            personRepository.delete(person);
            updateProjectLastEdited(projectId);
            if (projectId != null) {
                projectActivityService.recordForCurrentUser(
                        projectId,
                        ProjectActivity.ACTION_CHARACTER_DELETED,
                        "deleted character \"" + name + "\"",
                        ProjectActivity.ENTITY_PERSON,
                        id);
            }
        }
        return person;
    }

    private void updateProjectLastEdited(Integer projectId) {
        if (projectId != null) {
            projectRepository.findById(projectId).ifPresent(project -> {
                project.setLastEdited(java.time.LocalDateTime.now());
                projectRepository.save(project);
            });
        }
    }

    private boolean actorBelongsToProject(Actor actor, Project project) {
        if (actor == null || project == null) {
            return false;
        }
        for (Project actorProject : actor.getProjects()) {
            if (project.getId().equals(actorProject.getId())) {
                return true;
            }
        }
        return false;
    }

    private static String actorDisplayName(Actor actor) {
        if (actor == null) {
            return "an actor";
        }
        String first = actor.getFirstName() != null ? actor.getFirstName().trim() : "";
        String last = actor.getLastName() != null ? actor.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "an actor" : full;
    }
}
