package com.scripty.controller;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.dto.User;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.service.ProjectService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.SceneService;
import com.scripty.service.UserService;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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
    SceneService sceneService;

    @Autowired
    UserService userService;

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

        boolean isDefault = false;
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null && id.equals(currentUser.getDefaultProjectId())) {
                isDefault = true;
            }
        }

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("isDefault", isDefault);

        return "project/show";
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

    @RequestMapping(value = "/delete", method = RequestMethod.GET)
    public String deleteConfirm(@RequestParam Integer id, Model model) {
        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);
        model.addAttribute("viewModel", viewModel);
        return "project/delete";
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
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
    public String editNameInline(@RequestParam Integer id, Model model) {
        EditProjectViewModel viewModel = projectService.getEditProjectViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());
        return "project/editNameInline";
    }

    @RequestMapping(value = "/editNameInline", method = RequestMethod.POST)
    public String saveEditNameInline(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            EditProjectViewModel viewModel = projectService.getEditProjectViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            return "project/editNameInline";
        }
        Project project = projectService.saveEditProjectCommandModel(commandModel);
        projectVersionService.autoSaveVersion(project.getId());
        model.addAttribute("project", project);
        return "project/showNameInline";
    }

    @RequestMapping(value = "/showNameInline")
    public String showNameInline(@RequestParam Integer id, Model model) {
        Project project = projectService.read(id);
        model.addAttribute("project", project);
        return "project/showNameInline";
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

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateProjectCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateProjectViewModel viewModel = projectService.getCreateProjectViewModel();

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "project/create";
        }

        Project project = projectService.saveCreateProjectCommandModel(commandModel);

        CreateSceneCommandModel sceneCommandModel = new CreateSceneCommandModel();
        sceneCommandModel.setProjectId(project.getId());
        sceneCommandModel.setName(" ");
        Scene scene = sceneService.saveCreateSceneCommandModel(sceneCommandModel);

        return "redirect:/project/show?id=" + project.getId();
    }
}
