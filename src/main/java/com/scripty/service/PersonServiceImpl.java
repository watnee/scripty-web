package com.scripty.service;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Actor;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
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

    @Autowired
    public PersonServiceImpl(PersonRepository personRepository,
                             ActorRepository actorRepository,
                             ProjectRepository projectRepository) {
        this.personRepository = personRepository;
        this.actorRepository = actorRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public Person create(Person person) {
        return personRepository.save(person);
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

        List<Actor> actors = actorRepository.findAllByOrderByFirstNameAsc();
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
        List<Actor> allActors = actorRepository.findAllByOrderByFirstNameAsc();
        Project project = projectRepository.findById(person.getProject().getId()).orElse(null);

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
        Actor actor = actorRepository.findById(cmd.getActorId()).orElse(null);
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);

        person.setName(cmd.getName());
        person.setFullName(cmd.getFullName());
        if (actor != null) person.setActor(actor);
        if (project != null) person.setProject(project);
        return personRepository.save(person);
    }

    @Override
    public Person saveEditPersonCommandModel(EditPersonCommandModel cmd) {
        Person person = personRepository.findById(cmd.getId()).orElse(null);
        Actor actor = actorRepository.findById(cmd.getActorId()).orElse(null);
        Project project = projectRepository.findById(cmd.getProjectId()).orElse(null);

        person.setName(cmd.getName());
        person.setFullName(cmd.getFullName());
        person.setActor(actor);
        person.setProject(project);
        personRepository.save(person);
        return person;
    }

    @Override
    public Person deletePerson(Integer id) {
        Person person = personRepository.findById(id).orElse(null);
        personRepository.delete(person);
        return person;
    }
}
