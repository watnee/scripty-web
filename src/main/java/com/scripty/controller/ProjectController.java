package com.scripty.controller;

import com.scripty.api.HypermediaSupport;
import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.Team;
import com.scripty.dto.User;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectTeamViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.service.DocxExportService;
import com.scripty.service.FountainExportService;
import com.scripty.service.FountainImportService;
import com.scripty.service.PdfExportService;
import com.scripty.service.ProjectService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.InvitationService;
import com.scripty.service.ProjectActivityService;
import com.scripty.service.TeamService;
import com.scripty.service.TextDocumentService;
import com.scripty.service.UserService;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import com.scripty.commandmodel.invitation.SendInvitationCommandModel;
import com.scripty.security.ProjectAccessSupport;
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
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
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
    UserService userService;

    @Autowired
    TeamService teamService;

    @Autowired
    InvitationService invitationService;

    @Autowired
    ProjectActivityService projectActivityService;

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    ProjectAccessSupport projectAccess;

    private boolean denyProjectAccess(Integer projectId, Principal principal) {
        return !projectAccess.canAccessProject(projectId, principal);
    }

    private boolean denyScriptEdit(Integer projectId, Principal principal) {
        return !projectAccess.canEditScript(projectId, principal);
    }

    @Autowired
    FountainImportService fountainImportService;

    @Autowired
    FountainExportService fountainExportService;

    @Autowired
    PdfExportService pdfExportService;

    @Autowired
    DocxExportService docxExportService;

    @RequestMapping(value = "/list")
    public String list(Model model, Principal principal) {

        String userTeam = null;
        Integer defaultProjectId = null;
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null) {
                if (!currentUser.isAdmin() && !currentUser.isDirector() && !currentUser.isProducer() && !currentUser.isWriter() && !currentUser.isActor() && !currentUser.isCrew() && !currentUser.isDirectorOfPhotography()) {
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
    public String show(@RequestParam(required = false) Integer id,
                       @RequestParam(required = false) Integer editionId,
                       Model model,
                       Principal principal) {
        if (id == null) {
            if (principal != null) {
                User currentUser = userService.readByUsername(principal.getName());
                if (currentUser != null && currentUser.getDefaultProjectId() != null) {
                    return "redirect:/project/show?id=" + currentUser.getDefaultProjectId();
                }
            }
            return "redirect:/project/list";
        }

        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id, editionId);
        if (viewModel == null) {
            return "redirect:/project/list";
        }

        User currentUser = principal != null ? userService.readByUsername(principal.getName()) : null;
        if (currentUser == null || !projectService.canUserAccessProject(id, currentUser)) {
            return "redirect:/project/list";
        }

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("syncRevision", projectRevision(viewModel.getLastEdited()));
        model.addAttribute("canEditScript", projectAccess.canEditScript(id, currentUser));
        model.addAttribute("shareAccessUsers", projectService.getProjectShareAccessUsers(id));
        model.addAttribute("pendingInvitations", invitationService.getPendingInvitationsForProject(id, currentUser));
        List<Team> inviteTeams = filterAssignedTeams(viewModel.getTeams(), teamService.list());
        model.addAttribute("inviteTeams", inviteTeams);
        SendInvitationCommandModel inviteCommand = new SendInvitationCommandModel();
        inviteCommand.setProjectId(id);
        model.addAttribute("inviteCommand", inviteCommand);

        TextDocumentListViewModel documents = textDocumentService.getListViewModel(id, currentUser);
        List<Map<String, Object>> projectSongs = new ArrayList<>();
        if (documents != null && documents.getSongs() != null) {
            for (TextDocumentViewModel song : documents.getSongs()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", song.getId());
                item.put("title", song.getTitle() != null && !song.getTitle().isBlank() ? song.getTitle() : "Untitled song");
                projectSongs.add(item);
            }
        }
        model.addAttribute("projectSongs", projectSongs);

        List<Map<String, Object>> projectDrafts = new ArrayList<>();
        if (documents != null && documents.getDrafts() != null) {
            for (TextDocumentViewModel draft : documents.getDrafts()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", draft.getId());
                item.put("title", draft.getTitle() != null && !draft.getTitle().isBlank() ? draft.getTitle() : "Untitled draft");
                projectDrafts.add(item);
            }
        }
        model.addAttribute("projectDrafts", projectDrafts);

        return "project/show";
    }

    @RequestMapping(value = "/syncStatus", produces = MediaTypes.HAL_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<EntityModel<Map<String, Object>>> syncStatus(@RequestParam Integer id, @RequestParam(required = false) Long since, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        Project project = projectService.read(id);
        Map<String, Object> body = new HashMap<>();
        if (project == null) {
            body.put("exists", false);
            body.put("revision", since != null ? since : 0L);
            body.put("changed", false);
            return ResponseEntity.ok(HypermediaSupport.projectSyncStatus(body, id, since));
        }
        long revision = projectRevision(project.getLastEdited());
        body.put("exists", true);
        body.put("revision", revision);
        body.put("title", project.getTitle());
        body.put("changed", since == null || since < revision);
        return ResponseEntity.ok(HypermediaSupport.projectSyncStatus(body, id, since));
    }

    @RequestMapping(value = "/showScript")
    public String showScript(@RequestParam Integer id,
                             @RequestParam(required = false) Integer editionId,
                             Model model,
                             Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }
        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id, editionId);
        if (viewModel == null) {
            return "redirect:/project/list";
        }
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("canEditScript", projectAccess.canEditScript(id, principal));
        return "project/showScript";
    }

    @RequestMapping(value = "/read")
    public String read(@RequestParam Integer id, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }
        return "redirect:/scene/all?projectId=" + id;
    }

    @RequestMapping(value = "/undo", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<EntityModel<Map<String, Object>>> undo(@RequestParam Integer projectId,
                                                                 @RequestParam(required = false) Integer editionId,
                                                                 Principal principal) {
        if (denyScriptEdit(projectId, principal)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        ProjectUndoRedoService.UndoRedoResult result = projectUndoRedoService.undoWithDetails(projectId, editionId);
        return ResponseEntity.ok(HypermediaSupport.projectUndoRedo(buildUndoRedoResponse(result, projectId), projectId, true));
    }

    @RequestMapping(value = "/redo", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<EntityModel<Map<String, Object>>> redo(@RequestParam Integer projectId,
                                                                 @RequestParam(required = false) Integer editionId,
                                                                 Principal principal) {
        if (denyScriptEdit(projectId, principal)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        ProjectUndoRedoService.UndoRedoResult result = projectUndoRedoService.redoWithDetails(projectId, editionId);
        return ResponseEntity.ok(HypermediaSupport.projectUndoRedo(buildUndoRedoResponse(result, projectId), projectId, false));
    }

    @RequestMapping(value = "/undoRedoStatus", produces = MediaTypes.HAL_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<EntityModel<Map<String, Object>>> undoRedoStatus(@RequestParam Integer projectId,
                                                                           @RequestParam(required = false) Integer editionId,
                                                                           Principal principal) {
        if (denyProjectAccess(projectId, principal)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> status = new HashMap<>();
        status.put("canUndo", projectUndoRedoService.canUndo(projectId, editionId));
        status.put("canRedo", projectUndoRedoService.canRedo(projectId, editionId));
        return ResponseEntity.ok(HypermediaSupport.projectUndoRedoStatus(status, projectId));
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
                } else if (projectService.canUserAccessProject(id, currentUser)) {
                    currentUser.setDefaultProjectId(id);
                }
                userService.update(currentUser);
            }
        }
        String referer = request.getHeader("Referer");
        if (referer != null && referer.contains("/project/")) {
            // Only allow relative redirects back into this app's project pages.
            try {
                java.net.URI uri = java.net.URI.create(referer);
                String path = uri.getPath();
                String query = uri.getQuery();
                if (path != null && path.startsWith("/project/")) {
                    return "redirect:" + path + (query != null ? "?" + query : "");
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return "redirect:/project/list";
    }

    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }
        projectService.deleteProject(id);
        return "redirect:/project/list";
    }

    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }

        EditProjectViewModel viewModel = projectService.getEditProjectViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());

        return "project/edit";
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, Model model, Principal principal) {
        if (denyProjectAccess(commandModel.getId(), principal)) {
            return "redirect:/project/list";
        }

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
    public String editNameInline(@RequestParam Integer id, @RequestParam(required = false) String surface, Model model, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }
        EditProjectViewModel viewModel = projectService.getEditProjectViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());
        if ("breadcrumb".equals(surface)) {
            return "project/showBreadcrumbEditNameInline";
        }
        return "project/editNameInline";
    }

    @RequestMapping(value = "/editNameInline", method = RequestMethod.POST)
    public String saveEditNameInline(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, @RequestParam(required = false) String surface, Model model, Principal principal) {
        if (denyProjectAccess(commandModel.getId(), principal)) {
            return "redirect:/project/list";
        }
        if (bindingResult.hasErrors()) {
            EditProjectViewModel viewModel = projectService.getEditProjectViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            if ("breadcrumb".equals(surface)) {
                return "project/showBreadcrumbEditNameInline";
            }
            return "project/editNameInline";
        }
        Project project = projectService.saveEditProjectCommandModel(commandModel);
        projectVersionService.autoSaveVersion(project.getId());
        model.addAttribute("project", project);
        addDefaultProjectId(model, principal);
        if ("breadcrumb".equals(surface)) {
            return "project/showBreadcrumbNameInline";
        }
        return "project/showNameInline";
    }

    @RequestMapping(value = "/showNameInline")
    public String showNameInline(@RequestParam Integer id, Model model, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }
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
    public String production(@RequestParam(required = false) Integer id, Model model, Principal principal) {
        if (id == null || projectService.read(id) == null) {
            return "redirect:/project/list";
        }

        User currentUser = principal != null ? userService.readByUsername(principal.getName()) : null;
        if (currentUser == null || !projectService.canUserAccessProject(id, currentUser)) {
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
        model.addAttribute("pendingInvitations", invitationService.getPendingInvitationsForProject(id, currentUser));
        model.addAttribute("inviteTeams", filterAssignedTeams(viewModel.getTeams(), teams));
        SendInvitationCommandModel inviteCommand = new SendInvitationCommandModel();
        inviteCommand.setProjectId(id);
        model.addAttribute("inviteCommand", inviteCommand);
        model.addAttribute("recentActivity", projectActivityService.listRecent(id, currentUser, 20));
        return "project/production";
    }

    @RequestMapping(value = "/production/teams", method = RequestMethod.POST)
    public String setProductionTeams(@RequestParam Integer id,
                                     @RequestParam(value = "teamIds", required = false) List<Integer> teamIds,
                                     Principal principal) {
        if (projectService.read(id) == null || denyProjectAccess(id, principal)) {
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

    private List<Team> filterAssignedTeams(List<String> teamNames, List<Team> teams) {
        List<Team> assigned = new ArrayList<>();
        if (teamNames == null || teamNames.isEmpty()) {
            return assigned;
        }
        for (Team team : teams) {
            if (teamNames.contains(team.getName())) {
                assigned.add(team);
            }
        }
        return assigned;
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
            for (ProjectTeamViewModel team : project.getTeams()) {
                if (teamNames.contains(team.getName())) {
                    teamProductions.add(project);
                    break;
                }
            }
        }
        return teamProductions;
    }

    @RequestMapping(value = "/titlePage")
    public String titlePage(@RequestParam Integer id, Model model, Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return "redirect:/project/list";
        }
        TitlePageCommandModel commandModel = projectService.getTitlePageCommandModel(id);
        ProjectProfileViewModel projectViewModel = projectService.getProjectProfileViewModel(id);
        if (commandModel == null || projectViewModel == null) {
            return "redirect:/project/list";
        }

        model.addAttribute("project", projectViewModel);
        model.addAttribute("commandModel", commandModel);
        model.addAttribute("canEditScript", projectAccess.canEditScript(id, principal));

        return "project/titlePage";
    }

    @RequestMapping(value = "/titlePage", method = RequestMethod.POST)
    public String saveTitlePage(@Valid @ModelAttribute("commandModel") TitlePageCommandModel commandModel, BindingResult bindingResult, Model model, Principal principal) {
        if (denyScriptEdit(commandModel.getId(), principal)) {
            return "redirect:/project/list";
        }
        if (bindingResult.hasErrors()) {
            ProjectProfileViewModel projectViewModel = projectService.getProjectProfileViewModel(commandModel.getId());
            if (projectViewModel == null) {
                return "redirect:/project/list";
            }
            model.addAttribute("project", projectViewModel);
            model.addAttribute("commandModel", commandModel);
            model.addAttribute("canEditScript", projectAccess.canEditScript(commandModel.getId(), principal));
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
                               @RequestParam("file") MultipartFile file,
                               Principal principal) throws IOException {
        if (projectService.read(id) == null || denyScriptEdit(id, principal)) {
            return "redirect:/project/list";
        }
        if (file != null && !file.isEmpty()) {
            fountainImportService.importFileIntoProject(id, file);
        }
        return "redirect:/project/show?id=" + id;
    }

    @RequestMapping(value = "/export", method = RequestMethod.GET)
    public ResponseEntity<byte[]> exportScript(
            @RequestParam Integer id,
            @RequestParam(required = false, defaultValue = "fountain") String format,
            Principal principal) {
        if (denyProjectAccess(id, principal)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        Project project = projectService.read(id);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        String normalized = format == null ? "fountain" : format.trim().toLowerCase();
        if ("pdf".equals(normalized)) {
            byte[] pdf = pdfExportService.exportProject(id);
            String filename = exportFilename(project, "pdf");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(pdf);
        }
        if ("docx".equals(normalized) || "word".equals(normalized)) {
            byte[] docx = docxExportService.exportProject(id);
            String filename = exportFilename(project, "docx");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(docx);
        }

        String fountain = fountainExportService.exportProject(id);
        String filename = exportFilename(project, "fountain");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(fountain.getBytes(StandardCharsets.UTF_8));
    }

    private static String exportFilename(Project project, String extension) {
        String fallback = "script." + extension;
        String base = null;
        if (project.getScreenplayTitle() != null && !project.getScreenplayTitle().isBlank()) {
            base = project.getScreenplayTitle();
        } else if (project.getTitle() != null && !project.getTitle().isBlank()) {
            base = project.getTitle();
        }
        if (base == null) {
            return fallback;
        }
        String sanitized = base.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (sanitized.isBlank()) {
            return fallback;
        }
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80).replaceAll("[.-]+$", "");
        }
        return sanitized + "." + extension;
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

        return "redirect:/project/show?id=" + project.getId();
    }
}
