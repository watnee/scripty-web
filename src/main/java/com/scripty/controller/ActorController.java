/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.scripty.controller;

import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.dto.User;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;
import com.scripty.service.ActorService;
import com.scripty.service.ProjectService;
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

/**
 *
 * @author chris
 */
@Controller
@RequestMapping(value = "/actor")
public class ActorController {
    
    @Autowired
    ActorService actorService;

    @Autowired
    UserService userService;

    @Autowired
    ProjectService projectService;
    
    @RequestMapping(value = "/list")
    public String list(Model model, Principal principal) {

        Integer characterProjectId = resolveCharacterProjectId(principal);
        ActorListViewModel viewModel = actorService.getActorListViewModel(characterProjectId);
        viewModel.setCharacterProjectId(characterProjectId);

        model.addAttribute("viewModel", viewModel);

        return "actor/list";
    }

    private Integer resolveCharacterProjectId(Principal principal) {
        if (principal == null) {
            return null;
        }

        User currentUser = userService.readByUsername(principal.getName());
        if (currentUser == null) {
            return null;
        }

        if (currentUser.getDefaultProjectId() != null) {
            return currentUser.getDefaultProjectId();
        }

        String userTeam = null;
        if (!currentUser.isAdmin() && !currentUser.isDirector() && !currentUser.isProducer()) {
            userTeam = currentUser.getTeam();
        }

        ProjectListViewModel projects = projectService.getProjectListViewModel(userTeam);
        if (projects.getProjects().isEmpty()) {
            return null;
        }

        return projects.getProjects().get(0).getId();
    }
    
    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        ActorProfileViewModel viewModel = actorService.getActorProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "actor/show";
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        actorService.deleteActor(id);
        
        return "redirect:/actor/list";
    }
    
    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model) {

        EditActorViewModel viewModel = actorService.getEditActorViewModel(id);

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditActorCommandModel());

        return "actor/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditActorCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            EditActorViewModel viewModel = actorService.getEditActorViewModel(commandModel.getId());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "actor/edit";
        }

        Actor actor = actorService.saveEditActorCommandModel(commandModel);

        return "redirect:/actor/show?id=" + actor.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(Model model) {

        CreateActorViewModel viewModel = actorService.getCreateActorViewModel();

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateActorCommandModel());

        return "actor/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateActorCommandModel commandModel, BindingResult bindingResult, Model model) {

        if (bindingResult.hasErrors()) {
            CreateActorViewModel viewModel = actorService.getCreateActorViewModel();

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "actor/create";
        }

        Actor actor = actorService.saveCreateActorCommandModel(commandModel);

        return "redirect:/actor/show?id=" + actor.getId();
    }
}
