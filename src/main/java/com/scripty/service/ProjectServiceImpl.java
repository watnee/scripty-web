package com.scripty.service;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.Team;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TeamRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.block.BlockViewModel;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectTeamViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.PersonViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectShareUserViewModel;
import com.scripty.viewmodel.project.projectprofile.SceneViewModel;
import com.scripty.viewmodel.user.userprofile.UserProjectAccessViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final ProjectActivityService projectActivityService;
    private final ScriptEditionService scriptEditionService;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository,
                              PersonRepository personRepository,
                              BlockRepository blockRepository,
                              TeamRepository teamRepository,
                              UserService userService,
                              ProjectActivityService projectActivityService,
                              ScriptEditionService scriptEditionService) {
        this.projectRepository = projectRepository;
        this.personRepository = personRepository;
        this.blockRepository = blockRepository;
        this.teamRepository = teamRepository;
        this.userService = userService;
        this.projectActivityService = projectActivityService;
        this.scriptEditionService = scriptEditionService;
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
                if (canUserAccessProjectByTeam(project, userTeam)) {
                    filtered.add(project);
                }
            }
            projects = filtered;
        }

        vm.setProjects(mapProjectViewModels(projects));
        return vm;
    }

    @Override
    public boolean canUserAccessProject(Integer projectId, User user) {
        if (projectId == null || user == null) {
            return false;
        }
        Project project = projectRepository.findWithTeamsById(projectId).orElse(null);
        return canUserAccessProject(project, user);
    }

    @Override
    public boolean canUserAccessProject(Project project, User user) {
        if (project == null || user == null || !user.isEnabled()) {
            return false;
        }
        if (user.isAdmin() || user.isDirector() || user.isProducer() || user.isWriter() || user.isActor() || user.isCrew() || user.isDirectorOfPhotography() || user.isCastingDirector()) {
            return true;
        }
        String userTeam = user.getTeam();
        if (userTeam == null || userTeam.isEmpty()) {
            return project.getTeams() == null || project.getTeams().isEmpty();
        }
        return canUserAccessProjectByTeam(project, userTeam);
    }

    private boolean canUserAccessProjectByTeam(Project project, String userTeam) {
        if (project.getTeams() == null || project.getTeams().isEmpty()) {
            return true;
        }
        for (Team team : project.getTeams()) {
            if (userTeam.equals(team.getName())) {
                return true;
            }
        }
        return false;
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
            boolean canEdit = canEditScreenplay(user);
            ProjectShareUserViewModel vm = new ProjectShareUserViewModel();
            vm.setDisplayName(formatShareUserDisplayName(user));
            vm.setAccessLabel(formatShareUserAccessLabel(user));
            vm.setCanEdit(canEdit);
            vm.setPermissionLabel(permissionLabel(canEdit));
            users.add(vm);
        }

        users.sort(Comparator.comparing(ProjectShareUserViewModel::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return users;
    }

    @Override
    public List<UserProjectAccessViewModel> getUserProjectAccess(User user) {
        return buildUserProjectAccess(user, projectRepository.findAllWithTeams());
    }

    @Override
    public Map<Integer, List<UserProjectAccessViewModel>> getUsersProjectAccess(List<User> users) {
        Map<Integer, List<UserProjectAccessViewModel>> byUserId = new HashMap<>();
        if (users == null || users.isEmpty()) {
            return byUserId;
        }
        List<Project> projects = projectRepository.findAllWithTeams();
        for (User user : users) {
            if (user == null) {
                continue;
            }
            byUserId.put(user.getId(), buildUserProjectAccess(user, projects));
        }
        return byUserId;
    }

    private List<UserProjectAccessViewModel> buildUserProjectAccess(User user, List<Project> projects) {
        if (user == null || !user.isEnabled()) {
            return List.of();
        }

        List<UserProjectAccessViewModel> access = new ArrayList<>();
        boolean canEdit = canEditScreenplay(user);
        for (Project project : projects) {
            if (!canUserAccessProject(project, user)) {
                continue;
            }
            UserProjectAccessViewModel vm = new UserProjectAccessViewModel();
            vm.setProjectId(project.getId());
            vm.setProjectName(project.getTitle());
            vm.setCanEdit(canEdit);
            vm.setPermissionLabel(permissionLabel(canEdit));
            vm.setAccessReason(formatUserProjectAccessReason(project, user));
            access.add(vm);
        }

        access.sort(Comparator.comparing(UserProjectAccessViewModel::getProjectName, String.CASE_INSENSITIVE_ORDER));
        return access;
    }

    private static boolean canEditScreenplay(User user) {
        return user != null && (user.isWriter() || user.isAdmin());
    }

    private static String permissionLabel(boolean canEdit) {
        return canEdit ? "Can edit" : "View only";
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
        return formatPrivilegedRoleLabel(user);
    }

    private String formatUserProjectAccessReason(Project project, User user) {
        if (hasPrivilegedProjectRole(user)) {
            return formatPrivilegedRoleLabel(user);
        }
        if (project.getTeams() == null || project.getTeams().isEmpty()) {
            return "Open project";
        }
        String team = user.getTeam();
        if (team != null && !team.trim().isEmpty()) {
            return team.trim();
        }
        return "Open project";
    }

    private static boolean hasPrivilegedProjectRole(User user) {
        return user.isAdmin() || user.isDirector() || user.isProducer() || user.isWriter()
                || user.isActor() || user.isCrew() || user.isDirectorOfPhotography()
                || user.isCastingDirector();
    }

    private String formatPrivilegedRoleLabel(User user) {
        if (user.isAdmin()) {
            return "Admin";
        }
        if (user.isDirector()) {
            return "Director";
        }
        if (user.isProducer()) {
            return "Producer";
        }
        if (user.isWriter()) {
            return "Writer";
        }
        if (user.isActor()) {
            return "Actor";
        }
        if (user.isCrew()) {
            return "Crew";
        }
        if (user.isDirectorOfPhotography()) {
            return "Director of Photography";
        }
        if (user.isCastingDirector()) {
            return "Casting Director";
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
            pvm.setTeams(mapProjectTeams(project.getTeams()));
            pvm.setLastEdited(project.getLastEdited());
            projectViewModels.add(pvm);
        }
        return projectViewModels;
    }

    private List<ProjectTeamViewModel> mapProjectTeams(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return new ArrayList<>();
        }
        return teams.stream()
                .sorted(Comparator.comparing(Team::getName))
                .map(team -> {
                    ProjectTeamViewModel teamViewModel = new ProjectTeamViewModel();
                    teamViewModel.setId(team.getId());
                    teamViewModel.setName(team.getName());
                    return teamViewModel;
                })
                .collect(Collectors.toList());
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
        return getProjectProfileViewModel(id, null, true);
    }

    @Override
    public ProjectProfileViewModel getProjectProfileViewModel(Integer id, Integer editionId) {
        return getProjectProfileViewModel(id, editionId, true);
    }

    @Override
    public ProjectProfileViewModel getProjectProfileViewModel(Integer id, Integer editionId, boolean canBrowseEditions) {
        Project project = projectRepository.findWithTeamsById(id).orElse(null);
        if (project == null) {
            return null;
        }

        ScriptEdition edition = scriptEditionService.resolveForAccess(project.getId(), editionId, canBrowseEditions);
        if (edition == null) {
            edition = scriptEditionService.ensureDefaultEdition(project.getId());
        }

        ProjectProfileViewModel vm = new ProjectProfileViewModel();
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAsc(project.getId());
        List<Person> persons = edition != null
                ? personRepository.findByScriptEditionIdOrderByNameAsc(edition.getId())
                : personRepository.findByProjectIdOrderByNameAsc(project.getId());

        vm.setId(project.getId());
        vm.setTitle(project.getTitle());
        vm.setTeams(project.getTeamNames());
        vm.setLastEdited(edition != null && edition.getLastEdited() != null
                ? edition.getLastEdited()
                : project.getLastEdited());
        vm.setScreenplayTitle(project.getScreenplayTitle());
        vm.setWriters(project.getWriters());
        vm.setContactInfo(project.getContactInfo());
        vm.setScreenplayVersion(project.getScreenplayVersion());
        if (edition != null) {
            vm.setEditionId(edition.getId());
            vm.setEditionName(edition.getName());
        }
        vm.setEditions(scriptEditionService.getEditionViewModels(project.getId(), canBrowseEditions));

        List<BlockViewModel> blockViewModels = new ArrayList<>();
        Integer lastBlockId = null;
        for (Block block : blocks) {
            BlockViewModel bvm = new BlockViewModel();
            bvm.setId(block.getId());
            bvm.setOrder(block.getOrder());
            bvm.setContent(block.getContent());
            bvm.setBookmarked(block.isBookmarked());
            bvm.setPinned(block.isPinned());
            bvm.setType(block.getType());
            bvm.setTags(block.getTags());
            bvm.setTextAlign(block.getTextAlign());
            bvm.setFont(block.getFont());
            bvm.setHighlight(block.getHighlight());
            bvm.setTextBold(block.isTextBold());
            bvm.setTextItalic(block.isTextItalic());
            bvm.setTextUnderline(block.isTextUnderline());
            if (block.getPerson() != null) {
                Person person = personRepository.findById(block.getPerson().getId()).orElse(null);
                if (person != null) {
                    bvm.setPersonId(person.getId());
                    bvm.setPersonName(person.getName());
                }
            }
            blockViewModels.add(bvm);
            lastBlockId = block.getId();
        }
        SceneViewModel script = new SceneViewModel();
        script.setBlocks(blockViewModels);
        script.setLastBlockId(lastBlockId);
        vm.setScenes(List.of(script));

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
        project.setTitle(PlainTextSanitizer.sanitizeSingleLine(cmd.getTitle()));
        project.setLastEdited(java.time.LocalDateTime.now());
        projectRepository.save(project);
        scriptEditionService.ensureDefaultEdition(project.getId());
        if (cmd.getTeamIds() != null && !cmd.getTeamIds().isEmpty()) {
            applyTeams(project, cmd.getTeamIds());
        }
        projectActivityService.recordForCurrentUser(
                project.getId(),
                ProjectActivity.ACTION_PROJECT_CREATED,
                "created the project",
                ProjectActivity.ENTITY_PROJECT,
                project.getId());
        return project;
    }

    @Override
    public Project saveEditProjectCommandModel(EditProjectCommandModel cmd) {
        Project project = projectRepository.findWithTeamsById(cmd.getId()).orElse(null);
        String previousTitle = project.getTitle();
        project.setTitle(PlainTextSanitizer.sanitizeSingleLine(cmd.getTitle()));
        if (cmd.getTeamIds() != null) {
            applyTeams(project, cmd.getTeamIds());
        }
        project.setLastEdited(java.time.LocalDateTime.now());
        projectRepository.save(project);
        if (previousTitle == null || !previousTitle.equals(cmd.getTitle())) {
            projectActivityService.recordForCurrentUser(
                    project.getId(),
                    ProjectActivity.ACTION_PROJECT_RENAMED,
                    "renamed the project to \"" + cmd.getTitle() + "\"",
                    ProjectActivity.ENTITY_PROJECT,
                    project.getId());
        } else if (cmd.getTeamIds() != null) {
            projectActivityService.recordForCurrentUser(
                    project.getId(),
                    ProjectActivity.ACTION_TEAMS_UPDATED,
                    "updated project teams",
                    ProjectActivity.ENTITY_PROJECT,
                    project.getId());
        }
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
        projectActivityService.recordForCurrentUser(
                projectId,
                ProjectActivity.ACTION_TEAMS_UPDATED,
                "updated project teams",
                ProjectActivity.ENTITY_PROJECT,
                projectId);
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
        cmd.setScreenplayVersion(project.getScreenplayVersion());
        return cmd;
    }

    @Override
    public Project saveTitlePageCommandModel(TitlePageCommandModel cmd) {
        Project project = projectRepository.findById(cmd.getId()).orElse(null);
        if (project != null) {
            project.setScreenplayTitle(PlainTextSanitizer.sanitizeSingleLine(cmd.getScreenplayTitle()));
            project.setWriters(PlainTextSanitizer.sanitizeSingleLine(cmd.getWriters()));
            project.setContactInfo(PlainTextSanitizer.sanitize(cmd.getContactInfo()));
            project.setScreenplayVersion(PlainTextSanitizer.sanitizeSingleLine(cmd.getScreenplayVersion()));
            project.setLastEdited(java.time.LocalDateTime.now());
            projectRepository.save(project);
            projectActivityService.recordForCurrentUser(
                    project.getId(),
                    ProjectActivity.ACTION_TITLE_PAGE_UPDATED,
                    "updated the title page",
                    ProjectActivity.ENTITY_PROJECT,
                    project.getId());
        }
        return project;
    }
}
