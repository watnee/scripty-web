package com.scripty.controller;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.Scene;
import com.scripty.dto.User;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.service.ProjectService;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.SceneService;
import com.scripty.service.UserService;
import java.security.Principal;
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

@Controller
@RequestMapping(value = "/project")
public class ProjectController {

    @Autowired
    ProjectService projectService;

    @Autowired
    ProjectVersionService projectVersionService;

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
                userTeam = currentUser.getTeam();
                defaultProjectId = currentUser.getDefaultProjectId();
            }
        }

        ProjectListViewModel viewModel = projectService.getProjectListViewModel(userTeam);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("defaultProjectId", defaultProjectId);

        return "project/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model, Principal principal) {

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
