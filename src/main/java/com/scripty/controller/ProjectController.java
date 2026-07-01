/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.controller;

import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.dto.Project;
import com.scripty.viewmodel.project.createproject.CreateProjectViewModel;
import com.scripty.viewmodel.project.editproject.EditProjectViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.scripty.dto.Scene;
import com.scripty.service.ProjectService;
import com.scripty.service.SceneService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import java.security.Principal;

/**
 *
 * @author chris
 */
@Controller
@RequestMapping(value = "/project")
public class ProjectController {
    
    @Autowired
    ProjectService projectService;

    @Autowired
    SceneService sceneService;
    
    @RequestMapping(value = "/list")
    public String list(Model model) {

        ProjectListViewModel viewModel = projectService.getProjectListViewModel();

        model.addAttribute("viewModel", viewModel);

        return "project/list";
    }
    
    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model, Principal principal) {
        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);
        Project project = projectService.read(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("locked", project.isLocked());
        model.addAttribute("lockedBy", project.getLockedBy());
        model.addAttribute("isWriter", principal.getName().equals(project.getLockedBy()));
        model.addAttribute("lockedByOther", project.isLocked() && !principal.getName().equals(project.getLockedBy()));
        return "project/show";
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id, Principal principal) {
        if (projectService.isLockedByOther(id, principal.getName())) {
            return "redirect:/project/show?id=" + id;
        }
        projectService.deleteProject(id);
        return "redirect:/project/list";
    }

    @RequestMapping(value = "/lock", method = RequestMethod.POST)
    public String lock(@RequestParam Integer id, Principal principal) {
        projectService.lockProject(id, principal.getName());
        return "redirect:/project/show?id=" + id;
    }

    @RequestMapping(value = "/unlock", method = RequestMethod.POST)
    public String unlock(@RequestParam Integer id, Principal principal) {
        projectService.unlockProject(id, principal.getName());
        return "redirect:/project/show?id=" + id;
    }

    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model, Principal principal) {
        if (projectService.isLockedByOther(id, principal.getName())) {
            return "redirect:/project/show?id=" + id;
        }
        EditProjectViewModel viewModel = projectService.getEditProjectViewModel(id);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());
        return "project/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, Model model, Principal principal) {
        if (projectService.isLockedByOther(commandModel.getId(), principal.getName())) {
            return "redirect:/project/show?id=" + commandModel.getId();
        }
        if (bindingResult.hasErrors()) {
            EditProjectViewModel viewModel = projectService.getEditProjectViewModel(commandModel.getId());
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            return "project/edit";
        }
        Project project = projectService.saveEditProjectCommandModel(commandModel);
        return "redirect:/project/show?id=" + project.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(Model model) {

        CreateProjectViewModel viewModel = projectService.getCreateProjectViewModel();

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateProjectCommandModel());

        return "project/create";
    }

    // Handle Form Submission
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
