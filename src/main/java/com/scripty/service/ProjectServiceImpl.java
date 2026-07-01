package com.scripty.service;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SceneRepository;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.PersonViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.viewmodel.project.projectprofile.SceneViewModel;
import com.scripty.viewmodel.scene.sceneprofile.BlockViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final PersonRepository personRepository;
    private final BlockRepository blockRepository;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository,
                              SceneRepository sceneRepository,
                              PersonRepository personRepository,
                              BlockRepository blockRepository) {
        this.projectRepository = projectRepository;
        this.sceneRepository = sceneRepository;
        this.personRepository = personRepository;
        this.blockRepository = blockRepository;
    }

    @Override
    public Project read(Integer id) {
        return projectRepository.findById(id).orElse(null);
    }

    @Override
    public Project getProjectByScene(Scene scene) {
        return projectRepository.findBySceneId(scene.getId());
    }

    @Override
    public ProjectListViewModel getProjectListViewModel() {
        ProjectListViewModel vm = new ProjectListViewModel();
        List<Project> projects = projectRepository.findAllByOrderByTitleAsc();
        List<ProjectViewModel> projectViewModels = new ArrayList<>();
        for (Project project : projects) {
            ProjectViewModel pvm = new ProjectViewModel();
            pvm.setId(project.getId());
            pvm.setTitle(project.getTitle());
            pvm.setTeam(project.getTeam());
            projectViewModels.add(pvm);
        }
        vm.setProjects(projectViewModels);
        return vm;
    }

    @Override
    public ProjectListViewModel getProjectListViewModel(String userTeam) {
        ProjectListViewModel vm = new ProjectListViewModel();
        List<Project> projects = projectRepository.findAllByOrderByTitleAsc();

        if (userTeam != null && !userTeam.isEmpty()) {
            List<Project> filtered = new ArrayList<>();
            for (Project project : projects) {
                if (project.getTeam() == null || project.getTeam().isEmpty() || project.getTeam().equals(userTeam)) {
                    filtered.add(project);
                }
            }
            projects = filtered;
        }

        List<ProjectViewModel> projectViewModels = new ArrayList<>();
        for (Project project : projects) {
            ProjectViewModel pvm = new ProjectViewModel();
            pvm.setId(project.getId());
            pvm.setTitle(project.getTitle());
            pvm.setTeam(project.getTeam());
            projectViewModels.add(pvm);
        }
        vm.setProjects(projectViewModels);
        return vm;
    }

    @Override
    public ProjectProfileViewModel getProjectProfileViewModel(Integer id) {
        ProjectProfileViewModel vm = new ProjectProfileViewModel();
        Project project = projectRepository.findById(id).orElse(null);
        List<Scene> scenes = sceneRepository.findByProjectIdOrderByOrderAsc(project.getId());
        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());

        vm.setId(project.getId());
        vm.setTitle(project.getTitle());
        vm.setTeam(project.getTeam());

        List<SceneViewModel> sceneViewModels = new ArrayList<>();
        for (Scene scene : scenes) {
            SceneViewModel svm = new SceneViewModel();
            svm.setId(scene.getId());
            svm.setName(scene.getName());
            List<Block> blocks = blockRepository.findBySceneIdOrderByOrderAsc(scene.getId());
            List<BlockViewModel> blockViewModels = new ArrayList<>();
            for (Block block : blocks) {
                BlockViewModel bvm = new BlockViewModel();
                bvm.setId(block.getId());
                bvm.setOrder(block.getOrder());
                bvm.setContent(block.getContent());
                if (block.getPerson() != null) {
                    Person person = personRepository.findById(block.getPerson().getId()).orElse(null);
                    if (person != null) {
                        bvm.setPersonId(person.getId());
                        bvm.setPersonName(person.getName());
                    }
                }
                blockViewModels.add(bvm);
            }
            svm.setBlocks(blockViewModels);
            sceneViewModels.add(svm);
        }
        vm.setScenes(sceneViewModels);

        List<PersonViewModel> personViewModels = new ArrayList<>();
        for (Person person : persons) {
            PersonViewModel pvm = new PersonViewModel();
            pvm.setId(person.getId());
            pvm.setName(person.getName());
            personViewModels.add(pvm);
        }
        vm.setPersons(personViewModels);

        return vm;
    }

    @Override
    public CreateProjectViewModel getCreateProjectViewModel() {
        CreateProjectViewModel vm = new CreateProjectViewModel();
        vm.setCreateProjectCommandModel(new CreateProjectCommandModel());
        return vm;
    }

    @Override
    public EditProjectViewModel getEditProjectViewModel(Integer id) {
        EditProjectViewModel vm = new EditProjectViewModel();
        Project project = projectRepository.findById(id).orElse(null);
        vm.setId(id);
        EditProjectCommandModel commandModel = new EditProjectCommandModel();
        commandModel.setId(project.getId());
        commandModel.setTitle(project.getTitle());
        commandModel.setTeam(project.getTeam());
        vm.setEditProjectCommandModel(commandModel);
        return vm;
    }

    @Override
    public Project saveCreateProjectCommandModel(CreateProjectCommandModel cmd) {
        Project project = new Project();
        project.setTitle(cmd.getTitle());
        project.setTeam(cmd.getTeam());
        return projectRepository.save(project);
    }

    @Override
    public Project saveEditProjectCommandModel(EditProjectCommandModel cmd) {
        Project project = projectRepository.findById(cmd.getId()).orElse(null);
        project.setTitle(cmd.getTitle());
        project.setTeam(cmd.getTeam());
        projectRepository.save(project);
        return project;
    }

    @Override
    public Project deleteProject(Integer id) {
        Project project = projectRepository.findById(id).orElse(null);
        projectRepository.delete(project);
        return project;
    }
}
