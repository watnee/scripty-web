/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chriswatnee.martinis.controller;

import com.chriswatnee.martinis.commandmodel.project.createproject.CreateProjectCommandModel;
import com.chriswatnee.martinis.commandmodel.project.editproject.EditProjectCommandModel;
import com.chriswatnee.martinis.dto.Project;
import com.chriswatnee.martinis.viewmodel.project.createproject.CreateProjectViewModel;
import com.chriswatnee.martinis.viewmodel.project.editproject.EditProjectViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectlist.ProjectListViewModel;
import com.chriswatnee.martinis.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.chriswatnee.martinis.commandmodel.scene.createscene.CreateSceneCommandModel;
import com.chriswatnee.martinis.dto.Scene;
import com.chriswatnee.martinis.webservice.ProjectWebService;
import com.chriswatnee.martinis.webservice.SceneWebService;
import javax.inject.Inject;
import javax.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author chris
 */
@Controller
@RequestMapping(value = "/project")
public class ProjectController {
    
    @Inject
    ProjectWebService projectWebService;

    @Inject
    SceneWebService sceneWebService;
    
    @RequestMapping(value = "/list")
    public String list(Model model) {

        ProjectListViewModel viewModel = projectWebService.getProjectListViewModel();

        model.addAttribute("viewModel", viewModel);

        return "project/list";
    }
    
    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        ProjectProfileViewModel viewModel = projectWebService.getProjectProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "project/show";
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        projectWebService.deleteProject(id);
        
        return "redirect:/project/list";
    }
    
    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditProjectViewModel viewModel = projectWebService.getEditProjectViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditProjectCommandModel());

        return "project/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditProjectCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditProjectViewModel viewModel = projectWebService.getEditProjectViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "project/edit";
        }

        Project project = projectWebService.saveEditProjectCommandModel(commandModel);

        return "redirect:/project/show?id=" + project.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(Model model) {

        CreateProjectViewModel viewModel = projectWebService.getCreateProjectViewModel();

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateProjectCommandModel());

        return "project/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateProjectCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateProjectViewModel viewModel = projectWebService.getCreateProjectViewModel();

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "project/create";
        }

        Project project = projectWebService.saveCreateProjectCommandModel(commandModel);

        CreateSceneCommandModel sceneCommandModel = new CreateSceneCommandModel();
        sceneCommandModel.setProjectId(project.getId());
        sceneCommandModel.setName(" ");
        Scene scene = sceneWebService.saveCreateSceneCommandModel(sceneCommandModel);

        return "redirect:/scene/show?id=" + scene.getId();
    }
}
