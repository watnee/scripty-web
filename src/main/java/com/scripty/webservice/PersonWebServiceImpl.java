/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.webservice;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Actor;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.service.ActorService;
import com.scripty.service.PersonService;
import com.scripty.service.ProjectService;
import com.scripty.viewmodel.person.createperson.CreateActorViewModel;
import com.scripty.viewmodel.person.createperson.CreatePersonViewModel;
import com.scripty.viewmodel.person.editperson.EditActorViewModel;
import com.scripty.viewmodel.person.editperson.EditPersonViewModel;
import com.scripty.viewmodel.person.personlist.CharacterViewModel;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author chris
 */
public class PersonWebServiceImpl implements PersonWebService {
    
    PersonService personService;
    ActorService actorService;
    ProjectService projectService;

    @Inject
    public PersonWebServiceImpl(PersonService personService, ActorService actorService, ProjectService projectService) {
        this.personService = personService;
        this.actorService = actorService;
        this.projectService = projectService;
    }

    @Override
    public PersonListViewModel getPersonListViewModel(Integer projectId) {

        // Instantiate
        PersonListViewModel personListViewModel = new PersonListViewModel();

        // Look up stuff
        Project project = projectService.read(projectId);
        List<Person> persons = personService.getPersonsByProject(project);

        // Put stuff
        personListViewModel.setProjectId(project.getId());
        personListViewModel.setProjectTitle(project.getTitle());
        personListViewModel.setCharacters(translateCharacterViewModel(persons));

        return personListViewModel;
    }

    @Override
    public PersonProfileViewModel getPersonProfileViewModel(Integer id) {
        
        // Instantiate
        PersonProfileViewModel personProfileViewModel = new PersonProfileViewModel();

        // Look up stuff
        Person person = personService.read(id);

        Actor actor = null;
        if (person.getActor() != null) {
            actor = actorService.read(person.getActor().getId());
        }

        Project project = null;
        if (person.getProject() != null) {
            project = projectService.read(person.getProject().getId());
        }

        // Put stuff
        personProfileViewModel.setId(person.getId());
        personProfileViewModel.setName(person.getName());
        personProfileViewModel.setFullName(person.getFullName());

        if (actor != null) {
            personProfileViewModel.setActorId(actor.getId());
            personProfileViewModel.setActorName(actor.getFirstName() + " " + actor.getLastName());
        }

        if (project != null) {
            personProfileViewModel.setProjectId(project.getId());
            personProfileViewModel.setProjectTitle(project.getTitle());
        }
        
        return personProfileViewModel;
    }

    @Override
    public CreatePersonViewModel getCreatePersonViewModel(Integer projectId) {

        // Instantiate
        CreatePersonViewModel createPersonViewModel = new CreatePersonViewModel();

        CreatePersonCommandModel commandModel = new CreatePersonCommandModel();
        commandModel.setProjectId(projectId);
        
        createPersonViewModel.setCreatePersonCommandModel(commandModel);
        
        List<Actor> actors = actorService.list();
        
        // Populate
        createPersonViewModel.setProjectId(projectId);
        createPersonViewModel.setActors(translateCreateActorViewModel(actors));

        return createPersonViewModel;
    }

    @Override
    public EditPersonViewModel getEditPersonViewModel(Integer id) {

        // Instantiate
        EditPersonViewModel editPersonViewModel = new EditPersonViewModel();

        // Look up stuff
        Person existingPerson = personService.read(id);

        List<Actor> allActors = actorService.list();

        Actor selectedActor = null;
        if (existingPerson.getActor() != null) {
            selectedActor = actorService.read(existingPerson.getActor().getId());
        }
        
        Project selectedProject = projectService.read(existingPerson.getProject().getId());
        
        // Populate
        editPersonViewModel.setActors(translateEditActorViewModel(allActors));
        editPersonViewModel.setId(id);

        // Populate commmand model
        EditPersonCommandModel commandModel = new EditPersonCommandModel();
        commandModel.setId(existingPerson.getId());
        commandModel.setName(existingPerson.getName());
        commandModel.setFullName(existingPerson.getFullName());

        if (selectedActor != null) {
            commandModel.setActorId(selectedActor.getId());
        }
        
        commandModel.setProjectId(selectedProject.getId());

        editPersonViewModel.setEditPersonCommandModel(commandModel);

        return editPersonViewModel;
    }

    @Override
    public Person saveCreatePersonCommandModel(CreatePersonCommandModel createPersonCommandModel) {

        // Instantiate
        Person person = new Person();
        
        // Look up stuff
        Actor actor = actorService.read(createPersonCommandModel.getActorId());
        Project project = projectService.read(createPersonCommandModel.getProjectId()); 
        
        // Put stuff
        person.setName(createPersonCommandModel.getName());
        person.setFullName(createPersonCommandModel.getFullName());

        if (actor != null) {
            person.setActor(actor);
        }

        if (project != null) {
            person.setProject(project);
        }

        // Save stuff
        person = personService.create(person);
        
        return person;
    }

    @Override
    public Person saveEditPersonCommandModel(EditPersonCommandModel editPersonCommandModel) {

        // Instantiate
        Person person = personService.read(editPersonCommandModel.getId());
        
        // Look up stuff
        Actor actor = actorService.read(editPersonCommandModel.getActorId());
        Project project = projectService.read(editPersonCommandModel.getProjectId()); 

        // Put stuff
        person.setName(editPersonCommandModel.getName());
        person.setFullName(editPersonCommandModel.getFullName());
        person.setActor(actor);
        person.setProject(project);

        // Save stuff
        personService.update(person);

        return person;
    }
    
    @Override
    public Person deletePerson(Integer id) {

        // Instantiate
        Person person = personService.read(id);

        // Delete
        personService.delete(person);

        return person;
    }
    
    private List<CharacterViewModel> translateCharacterViewModel(List<Person> persons) {

        List<CharacterViewModel> characterViewModels = new ArrayList<>();

        for (Person person : persons) {
            CharacterViewModel characterViewModel = new CharacterViewModel();
            characterViewModel.setId(person.getId());
            characterViewModel.setName(person.getName());
            characterViewModels.add(characterViewModel);
        }

        return characterViewModels;
    }

    // Translate create actor
    private List<CreateActorViewModel> translateCreateActorViewModel(List<Actor> actors) {

        List<CreateActorViewModel> createActorViewModels = new ArrayList<>();

        for (Actor actor : actors) {
            CreateActorViewModel createActorViewModel = new CreateActorViewModel();
            createActorViewModel.setId(actor.getId());
            createActorViewModel.setName(actor.getFirstName() + " " + actor.getLastName());
            createActorViewModels.add(createActorViewModel);
        }

        return createActorViewModels;
    }
    
    // Translate edit actor
    private List<EditActorViewModel> translateEditActorViewModel(List<Actor> actors) {

        List<EditActorViewModel> editActorViewModels = new ArrayList<>();

        for (Actor actor : actors) {
            EditActorViewModel editActorViewModel = new EditActorViewModel();
            editActorViewModel.setId(actor.getId());
            editActorViewModel.setName(actor.getFirstName() + " " + actor.getLastName());
            editActorViewModels.add(editActorViewModel);
        }

        return editActorViewModels;
    }
    
}
