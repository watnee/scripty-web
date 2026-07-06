package com.scripty.controller;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.Team;
import com.scripty.dto.User;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.service.BlockService;
import com.scripty.service.FountainExportService;
import com.scripty.service.FountainImportService;
import com.scripty.service.ProjectService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.TeamService;
import com.scripty.service.UserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping(value = "/project")
public class ProjectController {

    @Autowired
    ProjectService projectService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectUndoRedoService projectUndoRedoService;

    @Autowired
    BlockService blockService;

    @Autowired
    UserService userService;

    @Autowired
    TeamService teamService;

    @Autowired
    FountainImportService fountainImportService;

    @Autowired
    FountainExportService fountainExportService;

    @RequestMapping(value = "/list")
    public String list(Model model, Principal principal) {

        String userTeam = null;
        Integer defaultProjectId = null;
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null) {
                if (!currentUser.isAdmin() && !currentUser.isDirector() && !currentUser.isProducer()) {
                    userTeam = currentUser.getTeam();
                }
                defaultProjectId = currentUser.getDefaultProjectId();
            }
        }

        ProjectListViewModel viewModel = projectService.getProjectListViewModel(userTeam);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("defaultProjectId", defaultProjectId);

        return "project/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam(required = false) Integer id, Model model, Principal principal) {
        if (id == null) {
            if (principal != null) {
                User currentUser = userService.readByUsername(principal.getName());
                if (currentUser != null && currentUser.getDefaultProjectId() != null) {
                    return "redirect:/project/show?id=" + currentUser.getDefaultProjectId();
                }
            }
            return "redirect:/project/list";
        }

        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("syncRevision", projectRevision(viewModel.getLastEdited()));
        model.addAttribute("shareAccessUsers", projectService.getProjectShareAccessUsers(id));

        return "project/show";
    }

    @RequestMapping(value = "/syncStatus")
    @ResponseBody
    public Map<String, Object> syncStatus(@RequestParam Integer id, @RequestParam(required = false) Long since) {
        Project project = projectService.read(id);
        Map<String, Object> body = new HashMap<>();
        long revision = projectRevision(project.getLastEdited());
        body.put("revision", revision);
        body.put("title", project.getTitle());
        body.put("changed", since == null || since < revision);
        return body;
    }

    @RequestMapping(value = "/showScript")
    public String showScript(@RequestParam Integer id, Model model) {
        model.addAttribute("viewModel", projectService.getProjectProfileViewModel(id));
        return "project/showScript";
    }

    @RequestMapping(value = "/read")
    public String read(@RequestParam Integer id) {
        return "redirect:/scene/all?projectId=" + id;
    }

    @RequestMapping(value = "/undo", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> undo(@RequestParam Integer projectId) {
        ProjectUndoRedoService.UndoRedoResult result = projectUndoRedoService.undoWithDetails(projectId);
        return buildUndoRedoResponse(result, projectId);
    }

    @RequestMapping(value = "/redo", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> redo(@RequestParam Integer projectId) {
        ProjectUndoRedoService.UndoRedoResult result = projectUndoRedoService.redoWithDetails(projectId);
        return buildUndoRedoResponse(result, projectId);
    }

    @RequestMapping(value = "/undoRedoStatus")
    @ResponseBody
    public Map<String, Object> undoRedoStatus(@RequestParam Integer projectId) {
        Map<String, Object> status = new HashMap<>();
        status.put("canUndo", projectUndoRedoService.canUndo(projectId));
        status.put("canRedo", projectUndoRedoService.canRedo(projectId));
        return status;
    }

    private long projectRevision(LocalDateTime lastEdited) {
        if (lastEdited == null) {
            return 0L;
        }
        return lastEdited.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Map<String, Object> buildUndoRedoResponse(ProjectUndoRedoService.UndoRedoResult result,
                                                      Integer projectId) {
        Map<String, Object> status = new HashMap<>();
        status.put("success", result.success());
        status.put("moveOnly", result.moveOnly());
        status.put("canUndo", projectUndoRedoService.canUndo(projectId));
        status.put("canRedo", projectUndoRedoService.canRedo(projectId));
        return status;
    }

    @RequestMapping(value = "/toggleDefault", method = RequestMethod.POST)
    public String toggleDefault(@RequestParam Integer id, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null) {
                if (id.equals(currentUser.getDefaultProjectId())) {
                    currentUser.setDefaultProjectId(null);
                } else {
                    currentUser.setDefaultProjectId(id);
                }
                userService.update(currentUser);
            }
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/project/list";
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        projectService.deleteProject(id);
        return "redirect:/project/list";
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditProjectViewModel viewModel = projectService.getEditProjectViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());

        return "project/edit";
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditProjectViewModel viewModel = projectService.getEditProjectViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "project/edit";
        }

        Project project = projectService.saveEditProjectCommandModel(commandModel);
        projectVersionService.autoSaveVersion(project.getId());

        return "redirect:/project/show?id=" + project.getId();
    }

    @RequestMapping(value = "/editNameInline")
    public String editNameInline(@RequestParam Integer id, @RequestParam(required = false) String surface, Model model) {
        EditProjectViewModel viewModel = projectService.getEditProjectViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());
        if ("header".equals(surface)) {
            return "project/showHeaderEditNameInline";
        }
        return "project/editNameInline";
    }

    @RequestMapping(value = "/editNameInline", method = RequestMethod.POST)
    public String saveEditNameInline(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, @RequestParam(required = false) String surface, Model model, Principal principal) {
        if (bindingResult.hasErrors()) {
            EditProjectViewModel viewModel = projectService.getEditProjectViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            if ("header".equals(surface)) {
                return "project/showHeaderEditNameInline";
            }
            return "project/editNameInline";
        }
        Project project = projectService.saveEditProjectCommandModel(commandModel);
        projectVersionService.autoSaveVersion(project.getId());
        model.addAttribute("project", project);
        addDefaultProjectId(model, principal);
        if ("header".equals(surface)) {
            return "project/showHeaderNameInline";
        }
        return "project/showNameInline";
    }

    @RequestMapping(value = "/showNameInline")
    public String showNameInline(@RequestParam Integer id, Model model, Principal principal) {
        Project project = projectService.readWithTeams(id);
        model.addAttribute("project", project);
        addDefaultProjectId(model, principal);
        return "project/showNameInline";
    }

    private void addDefaultProjectId(Model model, Principal principal) {
        Integer defaultProjectId = null;
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null) {
                defaultProjectId = currentUser.getDefaultProjectId();
            }
        }
        model.addAttribute("defaultProjectId", defaultProjectId);
    }

    @RequestMapping(value = "/production")
    public String production(@RequestParam(required = false) Integer id, Model model) {
        if (id == null || projectService.read(id) == null) {
            return "redirect:/project/list";
        }

        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);
        List<Team> teams = teamService.list();
        List<Integer> assignedTeamIds = mapAssignedTeamIds(viewModel.getTeams(), teams);
        List<ProjectViewModel> teamProductions = listTeamProductions(id, viewModel.getTeams());

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("teams", teams);
        model.addAttribute("assignedTeamIds", assignedTeamIds);
        model.addAttribute("teamProductions", teamProductions);
        model.addAttribute("shareAccessUsers", projectService.getProjectShareAccessUsers(id));
        return "project/production";
    }

    @RequestMapping(value = "/production/teams", method = RequestMethod.POST)
    public String setProductionTeams(@RequestParam Integer id,
                                     @RequestParam(value = "teamIds", required = false) List<Integer> teamIds) {
        if (projectService.read(id) == null) {
            return "redirect:/project/list";
        }
        projectService.setProjectTeams(id, teamIds);
        return "redirect:/project/production?id=" + id;
    }

    private List<Integer> mapAssignedTeamIds(List<String> teamNames, List<Team> teams) {
        List<Integer> assignedTeamIds = new ArrayList<>();
        if (teamNames == null || teamNames.isEmpty()) {
            return assignedTeamIds;
        }
        for (Team team : teams) {
            if (teamNames.contains(team.getName())) {
                assignedTeamIds.add(team.getId());
            }
        }
        return assignedTeamIds;
    }

    private List<ProjectViewModel> listTeamProductions(Integer projectId, List<String> teamNames) {
        List<ProjectViewModel> teamProductions = new ArrayList<>();
        if (teamNames == null || teamNames.isEmpty()) {
            return teamProductions;
        }

        ProjectListViewModel projects = projectService.getProjectListViewModel();
        for (ProjectViewModel project : projects.getProjects()) {
            if (project.getId() == projectId) {
                continue;
            }
            if (project.getTeams() == null) {
                continue;
            }
            for (String teamName : project.getTeams()) {
                if (teamNames.contains(teamName)) {
                    teamProductions.add(project);
                    break;
                }
            }
        }
        return teamProductions;
    }

    @RequestMapping(value = "/titlePage")
    public String titlePage(@RequestParam Integer id, Model model) {
        TitlePageCommandModel commandModel = projectService.getTitlePageCommandModel(id);
        ProjectProfileViewModel projectViewModel = projectService.getProjectProfileViewModel(id);

        model.addAttribute("project", projectViewModel);
        model.addAttribute("commandModel", commandModel);

        return "project/titlePage";
    }

    @RequestMapping(value = "/titlePage", method = RequestMethod.POST)
    public String saveTitlePage(@Valid @ModelAttribute("commandModel") TitlePageCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            ProjectProfileViewModel projectViewModel = projectService.getProjectProfileViewModel(commandModel.getId());
            model.addAttribute("project", projectViewModel);
            model.addAttribute("commandModel", commandModel);
            return "project/titlePage";
        }

        Project project = projectService.saveTitlePageCommandModel(commandModel);
        projectVersionService.autoSaveVersion(project.getId());

        return "redirect:/project/show?id=" + project.getId();
    }

    @RequestMapping(value = "/create")
    public String create(Model model) {

        CreateProjectViewModel viewModel = projectService.getCreateProjectViewModel();

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateProjectCommandModel());

        return "project/create";
    }

    @RequestMapping(value = "/import", method = RequestMethod.POST)
    public String importScript(@RequestParam Integer id,
                               @RequestParam("file") MultipartFile file) throws IOException {
        if (projectService.read(id) == null) {
            return "redirect:/project/list";
        }
        if (file != null && !file.isEmpty()) {
            fountainImportService.importFileIntoProject(id, file);
        }
        return "redirect:/project/show?id=" + id;
    }

    @RequestMapping(value = "/export", method = RequestMethod.GET)
    public ResponseEntity<byte[]> exportScript(@RequestParam Integer id) {
        Project project = projectService.read(id);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        String fountain = fountainExportService.exportProject(id);
        String filename = "script.fountain";
        if (project.getTitle() != null && !project.getTitle().isBlank()) {
            filename = project.getTitle().trim().replaceAll("[^a-zA-Z0-9._-]+", "-") + ".fountain";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(fountain.getBytes(StandardCharsets.UTF_8));
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateProjectCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateProjectViewModel viewModel = projectService.getCreateProjectViewModel();

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "project/create";
        }

        Project project = projectService.saveCreateProjectCommandModel(commandModel);
        blockService.createSceneBlock(project.getId(), " ");

        return "redirect:/project/show?id=" + project.getId();
    }
}
