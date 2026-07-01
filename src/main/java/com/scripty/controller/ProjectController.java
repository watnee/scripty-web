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
import com.scripty.service.SceneService;
import com.scripty.service.UserService;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
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
    SceneService sceneService;

    @Autowired
    UserService userService;

    @RequestMapping(value = "/list")
    public String list(Model model, Principal principal) {

        String userTeam = null;
        if (principal != null) {
            User currentUser = userService.readByUsername(principal.getName());
            if (currentUser != null) {
                userTeam = currentUser.getTeam();
            }
        }

        ProjectListViewModel viewModel = projectService.getProjectListViewModel(userTeam);

        model.addAttribute("viewModel", viewModel);

        return "project/list";
    }

    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "project/show";
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

        return "redirect:/scene/show?id=" + scene.getId();
    }
}
