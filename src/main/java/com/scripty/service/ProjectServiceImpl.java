package com.scripty.service;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SceneRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.dto.Team;
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
    private final TeamRepository teamRepository;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository,
                              SceneRepository sceneRepository,
                              PersonRepository personRepository,
                              BlockRepository blockRepository,
                              TeamRepository teamRepository) {
        this.projectRepository = projectRepository;
        this.sceneRepository = sceneRepository;
        this.personRepository = personRepository;
        this.blockRepository = blockRepository;
        this.teamRepository = teamRepository;
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
        vm.setLastEdited(project.getLastEdited());
        vm.setScreenplayTitle(project.getScreenplayTitle());
        vm.setWriters(project.getWriters());
        vm.setContactInfo(project.getContactInfo());

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
                bvm.setBookmarked(block.isBookmarked());
                bvm.setPinned(block.isPinned());
                bvm.setTags(block.getTags());
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
        
        List<Team> teams = teamRepository.findAllByOrderByNameAsc();
        List<String> teamNames = new ArrayList<>();
        for (Team t : teams) {
            teamNames.add(t.getName());
        }
        vm.setTeams(teamNames);
        
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
        
        List<Team> teams = teamRepository.findAllByOrderByNameAsc();
        List<String> teamNames = new ArrayList<>();
        for (Team t : teams) {
            teamNames.add(t.getName());
        }
        vm.setTeams(teamNames);
        
        return vm;
    }

    @Override
    public Project saveCreateProjectCommandModel(CreateProjectCommandModel cmd) {
        Project project = new Project();
        project.setTitle(cmd.getTitle());
        project.setTeam(cmd.getTeam());
        project.setLastEdited(java.time.LocalDateTime.now());
        return projectRepository.save(project);
    }

    @Override
    public Project saveEditProjectCommandModel(EditProjectCommandModel cmd) {
        Project project = projectRepository.findById(cmd.getId()).orElse(null);
        project.setTitle(cmd.getTitle());
        project.setTeam(cmd.getTeam());
        project.setLastEdited(java.time.LocalDateTime.now());
        projectRepository.save(project);
        return project;
    }

    @Override
    public Project deleteProject(Integer id) {
        Project project = projectRepository.findById(id).orElse(null);
        projectRepository.delete(project);
        return project;
    }

    @Override
    public TitlePageCommandModel getTitlePageCommandModel(Integer id) {
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null) return null;
        TitlePageCommandModel cmd = new TitlePageCommandModel();
        cmd.setId(project.getId());
        cmd.setScreenplayTitle(project.getScreenplayTitle());
        cmd.setWriters(project.getWriters());
        cmd.setContactInfo(project.getContactInfo());
        return cmd;
    }

    @Override
    public Project saveTitlePageCommandModel(TitlePageCommandModel cmd) {
        Project project = projectRepository.findById(cmd.getId()).orElse(null);
        if (project != null) {
            project.setScreenplayTitle(cmd.getScreenplayTitle());
            project.setWriters(cmd.getWriters());
            project.setContactInfo(cmd.getContactInfo());
            project.setLastEdited(java.time.LocalDateTime.now());
            projectRepository.save(project);
        }
        return project;
    }
}
