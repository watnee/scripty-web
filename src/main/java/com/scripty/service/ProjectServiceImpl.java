package com.scripty.service;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.Team;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.viewmodel.block.BlockViewModel;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.PersonViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectShareUserViewModel;
import com.scripty.viewmodel.project.projectprofile.SceneViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final PersonRepository personRepository;
    private final BlockRepository blockRepository;
    private final TeamRepository teamRepository;
    private final UserService userService;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository,
                              PersonRepository personRepository,
                              BlockRepository blockRepository,
                              TeamRepository teamRepository,
                              UserService userService) {
        this.projectRepository = projectRepository;
        this.personRepository = personRepository;
        this.blockRepository = blockRepository;
        this.teamRepository = teamRepository;
        this.userService = userService;
    }

    @Override
    public Project read(Integer id) {
        return projectRepository.findById(id).orElse(null);
    }

    @Override
    public Project readWithTeams(Integer id) {
        return projectRepository.findWithTeamsById(id).orElse(null);
    }

    @Override
    public Project getProjectByBlock(Block block) {
        return block != null && block.getProject() != null
                ? projectRepository.findById(block.getProject().getId()).orElse(null)
                : null;
    }

    @Override
    public ProjectListViewModel getProjectListViewModel() {
        ProjectListViewModel vm = new ProjectListViewModel();
        List<Project> projects = new ArrayList<>(projectRepository.findAllWithTeams());
        vm.setProjects(mapProjectViewModels(projects));
        return vm;
    }

    @Override
    public ProjectListViewModel getProjectListViewModel(String userTeam) {
        ProjectListViewModel vm = new ProjectListViewModel();
        List<Project> projects = new ArrayList<>(projectRepository.findAllWithTeams());

        if (userTeam != null && !userTeam.isEmpty()) {
            List<Project> filtered = new ArrayList<>();
            for (Project project : projects) {
                if (canUserAccessProject(project, userTeam)) {
                    filtered.add(project);
                }
            }
            projects = filtered;
        }

        vm.setProjects(mapProjectViewModels(projects));
        return vm;
    }

    private boolean canUserAccessProject(Project project, String userTeam) {
        if (project.getTeams().isEmpty()) {
            return true;
        }
        for (Team team : project.getTeams()) {
            if (userTeam.equals(team.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean canUserAccessProject(Project project, User user) {
        if (!user.isEnabled()) {
            return false;
        }
        if (user.isAdmin() || user.isDirector() || user.isProducer()) {
            return true;
        }
        String userTeam = user.getTeam();
        if (userTeam == null || userTeam.isEmpty()) {
            return project.getTeams().isEmpty();
        }
        return canUserAccessProject(project, userTeam);
    }

    @Override
    public List<ProjectShareUserViewModel> getProjectShareAccessUsers(Integer projectId) {
        Project project = projectRepository.findWithTeamsById(projectId).orElse(null);
        if (project == null) {
            return List.of();
        }

        List<ProjectShareUserViewModel> users = new ArrayList<>();
        for (User user : userService.list()) {
            if (!canUserAccessProject(project, user)) {
                continue;
            }
            ProjectShareUserViewModel vm = new ProjectShareUserViewModel();
            vm.setDisplayName(formatShareUserDisplayName(user));
            vm.setAccessLabel(formatShareUserAccessLabel(user));
            users.add(vm);
        }

        users.sort(Comparator.comparing(ProjectShareUserViewModel::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    private String formatShareUserDisplayName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (first + " " + last).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        return user.getUsername();
    }

    private String formatShareUserAccessLabel(User user) {
        if (user.isAdmin()) {
            return "Admin";
        }
        if (user.isDirector()) {
            return "Director";
        }
        if (user.isProducer()) {
            return "Producer";
        }
        String team = user.getTeam();
        if (team == null || team.trim().isEmpty()) {
            return "All projects";
        }
        return team.trim();
    }

    private List<ProjectViewModel> mapProjectViewModels(List<Project> projects) {
        sortProjectsByLastEdited(projects);
        List<ProjectViewModel> projectViewModels = new ArrayList<>();
        for (Project project : projects) {
            ProjectViewModel pvm = new ProjectViewModel();
            pvm.setId(project.getId());
            pvm.setTitle(project.getTitle());
            pvm.setTeams(project.getTeamNames());
            pvm.setLastEdited(project.getLastEdited());
            projectViewModels.add(pvm);
        }
        return projectViewModels;
    }

    private void sortProjectsByLastEdited(List<Project> projects) {
        projects.sort((a, b) -> {
            java.time.LocalDateTime aEdited = a.getLastEdited();
            java.time.LocalDateTime bEdited = b.getLastEdited();
            if (aEdited == null && bEdited == null) {
                return a.getTitle().compareToIgnoreCase(b.getTitle());
            }
            if (aEdited == null) {
                return 1;
            }
            if (bEdited == null) {
                return -1;
            }
            int editedCompare = bEdited.compareTo(aEdited);
            if (editedCompare != 0) {
                return editedCompare;
            }
            return a.getTitle().compareToIgnoreCase(b.getTitle());
        });
    }

    @Override
    public ProjectProfileViewModel getProjectProfileViewModel(Integer id) {
        ProjectProfileViewModel vm = new ProjectProfileViewModel();
        Project project = projectRepository.findWithTeamsById(id).orElse(null);
        List<Block> blocks = blockRepository.findByProjectIdOrderByOrderAsc(project.getId());
        List<Person> persons = personRepository.findByProjectIdOrderByNameAsc(project.getId());

        vm.setId(project.getId());
        vm.setTitle(project.getTitle());
        vm.setTeams(project.getTeamNames());
        vm.setLastEdited(project.getLastEdited());
        vm.setScreenplayTitle(project.getScreenplayTitle());
        vm.setWriters(project.getWriters());
        vm.setContactInfo(project.getContactInfo());

        List<SceneViewModel> sceneViewModels = new ArrayList<>();
        SceneViewModel currentScene = null;
        for (Block block : blocks) {
            if (block.isScene()) {
                currentScene = new SceneViewModel();
                currentScene.setId(block.getId());
                currentScene.setName(block.getContent());
                currentScene.setLastBlockId(block.getId());
                currentScene.setBlocks(new ArrayList<>());
                sceneViewModels.add(currentScene);
                continue;
            }
            if (currentScene == null) {
                currentScene = new SceneViewModel();
                currentScene.setBlocks(new ArrayList<>());
                sceneViewModels.add(currentScene);
            }
            BlockViewModel bvm = new BlockViewModel();
            bvm.setId(block.getId());
            bvm.setOrder(block.getOrder());
            bvm.setContent(block.getContent());
            bvm.setBookmarked(block.isBookmarked());
            bvm.setPinned(block.isPinned());
            bvm.setType(block.getType());
            bvm.setTags(block.getTags());
            if (block.getPerson() != null) {
                Person person = personRepository.findById(block.getPerson().getId()).orElse(null);
                if (person != null) {
                    bvm.setPersonId(person.getId());
                    bvm.setPersonName(person.getName());
                }
            }
            currentScene.getBlocks().add(bvm);
            currentScene.setLastBlockId(block.getId());
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
        vm.setAvailableTeams(teamRepository.findAllByOrderByNameAsc());
        return vm;
    }

    @Override
    public EditProjectViewModel getEditProjectViewModel(Integer id) {
        EditProjectViewModel vm = new EditProjectViewModel();
        Project project = projectRepository.findWithTeamsById(id).orElse(null);
        vm.setId(id);
        EditProjectCommandModel commandModel = new EditProjectCommandModel();
        commandModel.setId(project.getId());
        commandModel.setTitle(project.getTitle());
        commandModel.setTeamIds(mapTeamIds(project.getTeams()));
        vm.setEditProjectCommandModel(commandModel);
        vm.setAvailableTeams(teamRepository.findAllByOrderByNameAsc());
        return vm;
    }

    @Override
    public Project saveCreateProjectCommandModel(CreateProjectCommandModel cmd) {
        Project project = new Project();
        project.setTitle(cmd.getTitle());
        project.setLastEdited(java.time.LocalDateTime.now());
        projectRepository.save(project);
        if (cmd.getTeamIds() != null && !cmd.getTeamIds().isEmpty()) {
            applyTeams(project, cmd.getTeamIds());
        }
        return project;
    }

    @Override
    public Project saveEditProjectCommandModel(EditProjectCommandModel cmd) {
        Project project = projectRepository.findWithTeamsById(cmd.getId()).orElse(null);
        project.setTitle(cmd.getTitle());
        if (cmd.getTeamIds() != null) {
            applyTeams(project, cmd.getTeamIds());
        }
        project.setLastEdited(java.time.LocalDateTime.now());
        projectRepository.save(project);
        return project;
    }

    @Override
    @Transactional
    public void setProjectTeams(Integer projectId, List<Integer> teamIds) {
        Project project = projectRepository.findWithTeamsById(projectId).orElse(null);
        if (project == null) {
            return;
        }
        applyTeams(project, teamIds != null ? teamIds : List.of());
        project.setLastEdited(java.time.LocalDateTime.now());
        projectRepository.save(project);
    }

    private void applyTeams(Project project, List<Integer> teamIds) {
        List<Team> selectedTeams = teamIds.isEmpty()
                ? new ArrayList<>()
                : new ArrayList<>(teamRepository.findAllById(teamIds));
        selectedTeams.sort(Comparator.comparing(Team::getName));
        project.setTeams(selectedTeams);
    }

    private List<Integer> mapTeamIds(List<Team> teams) {
        List<Integer> teamIds = new ArrayList<>();
        for (Team team : teams) {
            teamIds.add(team.getId());
        }
        return teamIds;
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
