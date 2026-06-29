/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chriswatnee.martinis.webservice;

import com.chriswatnee.martinis.commandmodel.project.createproject.CreateProjectCommandModel;
import com.chriswatnee.martinis.commandmodel.project.editproject.EditProjectCommandModel;
import com.chriswatnee.martinis.dto.Block;
import com.chriswatnee.martinis.dto.Person;
import com.chriswatnee.martinis.dto.Project;
import com.chriswatnee.martinis.dto.Scene;
import com.chriswatnee.martinis.service.BlockService;
import com.chriswatnee.martinis.service.PersonService;
import com.chriswatnee.martinis.service.ProjectService;
import com.chriswatnee.martinis.service.SceneService;
import com.chriswatnee.martinis.viewmodel.scene.sceneprofile.BlockViewModel;
import com.chriswatnee.martinis.viewmodel.project.createproject.CreateProjectViewModel;
import com.chriswatnee.martinis.viewmodel.project.editproject.EditProjectViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectlist.ProjectListViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectlist.ProjectViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectprofile.PersonViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectprofile.SceneViewModel;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 *
 * @author chris
 */
public class ProjectWebServiceImpl implements ProjectWebService {

    ProjectService projectService;
    SceneService sceneService;
    PersonService personService;
    BlockService blockService;

    @Inject
    public ProjectWebServiceImpl(ProjectService projectService, SceneService sceneService, PersonService personService, BlockService blockService) {
        this.projectService = projectService;
        this.sceneService = sceneService;
        this.personService = personService;
        this.blockService = blockService;
    }
    
    @Override
    public ProjectListViewModel getProjectListViewModel() {

        // Instantiate
        ProjectListViewModel projectListViewModel = new ProjectListViewModel();

        // Look stuff up
        List<Project> projects = projectService.list();

        // Put stuff in
        projectListViewModel.setProjects(translate(projects));

        return projectListViewModel;
    }

    @Override
    public ProjectProfileViewModel getProjectProfileViewModel(Integer id) {
        
        // Instantiate
        ProjectProfileViewModel projectProfileViewModel = new ProjectProfileViewModel();

        // Look up stuff
        Project project = projectService.read(id);
        List<Scene> scenes = sceneService.getScenesByProject(project);
        List<Person> persons = personService.getPersonsByProject(project);

        // Put stuff
        projectProfileViewModel.setId(project.getId());
        projectProfileViewModel.setTitle(project.getTitle());
        projectProfileViewModel.setScenes(translateScene(scenes));
        projectProfileViewModel.setPersons(translatePerson(persons));

        return projectProfileViewModel;
    }

    @Override
    public CreateProjectViewModel getCreateProjectViewModel() {

        // Instantiate
        CreateProjectViewModel createProjectViewModel = new CreateProjectViewModel();

        CreateProjectCommandModel commandModel = new CreateProjectCommandModel();
        createProjectViewModel.setCreateProjectCommandModel(commandModel);

        return createProjectViewModel;
    }

    @Override
    public EditProjectViewModel getEditProjectViewModel(Integer id) {

        // Instantiate
        EditProjectViewModel editProjectViewModel = new EditProjectViewModel();

        // Look up stuff
        Project existingProject = projectService.read(id);

        // Populate
        editProjectViewModel.setId(id);

        // Populate commmand model
        EditProjectCommandModel commandModel = new EditProjectCommandModel();
        commandModel.setId(existingProject.getId());
        commandModel.setTitle(existingProject.getTitle());

        editProjectViewModel.setEditProjectCommandModel(commandModel);

        return editProjectViewModel;
    }

    @Override
    public Project saveCreateProjectCommandModel(CreateProjectCommandModel createProjectCommandModel) {

        // Instantiate
        Project project = new Project();
        
        // Put stuff
        project.setTitle(createProjectCommandModel.getTitle());

        // Save stuff
        project = projectService.create(project);
        
        return project;
    }

    @Override
    public Project saveEditProjectCommandModel(EditProjectCommandModel editProjectCommandModel) {

        // Instantiate
        Project project = projectService.read(editProjectCommandModel.getId());

        // Put stuff
        project.setTitle(editProjectCommandModel.getTitle());

        // Save stuff
        projectService.update(project);

        return project;
    }
    
    @Override
    public Project deleteProject(Integer id) {

        // Instantiate
        Project project = projectService.read(id);

        // Delete
        projectService.delete(project);

        return project;
    }

    private List<SceneViewModel> translateScene(List<Scene> scenes) {
        List<SceneViewModel> sceneViewModels = new ArrayList<>();

        for (Scene scene : scenes) {
            sceneViewModels.add(translateScene(scene));
        }

        return sceneViewModels;
    }

    private SceneViewModel translateScene(Scene scene) {

        SceneViewModel sceneViewModel = new SceneViewModel();

        sceneViewModel.setName(scene.getName());
        sceneViewModel.setId(scene.getId());

        List<Block> blocks = blockService.getBlocksByScene(scene);
        List<BlockViewModel> blockViewModels = new ArrayList<>();
        for (Block block : blocks) {
            BlockViewModel bvm = new BlockViewModel();
            bvm.setId(block.getId());
            bvm.setOrder(block.getOrder());
            bvm.setContent(block.getContent());
            if (block.getPerson() != null) {
                Person person = personService.read(block.getPerson().getId());
                if (person != null) {
                    bvm.setPersonId(person.getId());
                    bvm.setPersonName(person.getName());
                }
            }
            blockViewModels.add(bvm);
        }
        sceneViewModel.setBlocks(blockViewModels);

        return sceneViewModel;
    }

    private List<PersonViewModel> translatePerson(List<Person> persons) {
        List<PersonViewModel> personViewModels = new ArrayList<>();

        for (Person person : persons) {
            personViewModels.add(translatePerson(person));
        }

        return personViewModels;
    }

    private PersonViewModel translatePerson(Person person) {

        PersonViewModel personViewModel = new PersonViewModel();

        personViewModel.setName(person.getName());
        personViewModel.setId(person.getId());

        return personViewModel;
    }


    private List<ProjectViewModel> translate(List<Project> projects) {
        List<ProjectViewModel> projectViewModels = new ArrayList<>();

        for (Project project : projects) {
            projectViewModels.add(translate(project));
        }

        return projectViewModels;
    }

    private ProjectViewModel translate(Project project) {

        ProjectViewModel projectViewModel = new ProjectViewModel();

        projectViewModel.setTitle(project.getTitle());
        projectViewModel.setId(project.getId());

        return projectViewModel;
    }
    
}
