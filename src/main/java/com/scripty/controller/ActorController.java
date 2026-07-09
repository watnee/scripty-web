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
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import com.scripty.viewmodel.actor.createactor.CreateActorViewModel;
import com.scripty.viewmodel.actor.editactor.EditActorViewModel;
import com.scripty.service.ActorService;
import com.scripty.service.ActorHeadshotService;
import com.scripty.service.ProjectService;
import com.scripty.service.UserService;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

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
    ActorHeadshotService actorHeadshotService;

    @Autowired
    UserService userService;

    @Autowired
    ProjectService projectService;
    
    @RequestMapping(value = "/list")
    public String list(@RequestParam(required = false) Integer projectId, Model model, Principal principal) {

        UserProjectContext context = resolveUserProjectContext(principal);
        Integer selectedProjectId = resolveSelectedProjectId(projectId, context);

        ActorListViewModel viewModel = actorService.getActorListViewModel(selectedProjectId);
        viewModel.setCharacterProjectId(selectedProjectId);
        viewModel.setProjects(context.projects().getProjects());

        model.addAttribute("viewModel", viewModel);

        return "actor/list";
    }

    private Integer resolveSelectedProjectId(Integer requestedProjectId, UserProjectContext context) {
        if (requestedProjectId != null && isAccessibleProject(requestedProjectId, context.projects())) {
            return requestedProjectId;
        }

        if (context.defaultProjectId() != null
                && isAccessibleProject(context.defaultProjectId(), context.projects())) {
            return context.defaultProjectId();
        }

        if (context.projects().getProjects().isEmpty()) {
            return null;
        }

        return context.projects().getProjects().get(0).getId();
    }

    private boolean isAccessibleProject(Integer projectId, ProjectListViewModel projects) {
        for (ProjectViewModel project : projects.getProjects()) {
            if (project.getId() == projectId) {
                return true;
            }
        }
        return false;
    }

    private UserProjectContext resolveUserProjectContext(Principal principal) {
        if (principal == null) {
            return new UserProjectContext(null, projectService.getProjectListViewModel());
        }

        User currentUser = userService.readByUsername(principal.getName());
        if (currentUser == null) {
            return new UserProjectContext(null, projectService.getProjectListViewModel());
        }

        String userTeam = null;
        if (!currentUser.isAdmin() && !currentUser.isDirector() && !currentUser.isProducer() && !currentUser.isWriter() && !currentUser.isActor() && !currentUser.isCrew() && !currentUser.isDirectorOfPhotography()) {
            userTeam = currentUser.getTeam();
        }

        return new UserProjectContext(currentUser.getDefaultProjectId(),
                projectService.getProjectListViewModel(userTeam));
    }

    private record UserProjectContext(Integer defaultProjectId, ProjectListViewModel projects) {
    }
    
    @RequestMapping(value = "/show")
    public String show(@RequestParam Integer id, Model model) {

        ActorProfileViewModel viewModel = actorService.getActorProfileViewModel(id);

        model.addAttribute("viewModel", viewModel);

        return "actor/show";
    }

    @GetMapping("/headshot")
    public ResponseEntity<Resource> headshot(@RequestParam Integer id) {
        Resource resource = actorHeadshotService.loadHeadshot(id).orElse(null);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = actorHeadshotService.getContentType(id)
                .map(MediaType::parseMediaType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
    
    @RequestMapping(value = "/delete")
    public String delete(@RequestParam Integer id) {
        
        Actor actor = actorService.deleteActor(id);
        if (!actor.getProjects().isEmpty()) {
            return "redirect:/actor/list?projectId=" + actor.getProjects().get(0).getId();
        }
        return "redirect:/actor/list";
    }
    
    // Show Form
    @RequestMapping(value = "/edit")
    public String edit(@RequestParam Integer id, Model model, Principal principal) {

        UserProjectContext context = resolveUserProjectContext(principal);
        EditActorViewModel viewModel = actorService.getEditActorViewModel(id);
        viewModel.setProjects(context.projects().getProjects());

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getEditActorCommandModel());

        return "actor/edit";
    }

    // Handle Form Submission
    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    public String saveEdit(@Valid @ModelAttribute("commandModel") EditActorCommandModel commandModel,
                           BindingResult bindingResult,
                           @RequestParam(value = "headshot", required = false) MultipartFile headshot,
                           @RequestParam(value = "removeHeadshot", defaultValue = "false") boolean removeHeadshot,
                           Model model,
                           Principal principal) {

        if (bindingResult.hasErrors()) {
            return renderEditForm(commandModel, model, principal);
        }

        Actor actor = actorService.saveEditActorCommandModel(commandModel);

        try {
            actorHeadshotService.updateHeadshot(actor, headshot, removeHeadshot);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("headshot", ex.getMessage());
            return renderEditForm(commandModel, model, principal);
        }

        return "redirect:/actor/show?id=" + actor.getId();
    }
    
    // Show Form
    @RequestMapping(value = "/create")
    public String create(@RequestParam(required = false) Integer projectId, Model model, Principal principal) {

        UserProjectContext context = resolveUserProjectContext(principal);
        Integer selectedProjectId = resolveSelectedProjectId(projectId, context);

        CreateActorViewModel viewModel = actorService.getCreateActorViewModel(selectedProjectId);
        viewModel.setProjects(context.projects().getProjects());

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", viewModel.getCreateActorCommandModel());

        return "actor/create";
    }

    // Handle Form Submission
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String saveCreate(@Valid @ModelAttribute("commandModel") CreateActorCommandModel commandModel,
                             BindingResult bindingResult,
                             @RequestParam(value = "headshot", required = false) MultipartFile headshot,
                             Model model,
                             Principal principal) {

        if (bindingResult.hasErrors()) {
            UserProjectContext context = resolveUserProjectContext(principal);
            CreateActorViewModel viewModel = actorService.getCreateActorViewModel(commandModel.getProjectIds().isEmpty()
                    ? null
                    : commandModel.getProjectIds().get(0));
            viewModel.setProjects(context.projects().getProjects());

            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);

            return "actor/create";
        }

        Actor actor = actorService.saveCreateActorCommandModel(commandModel);

        try {
            actorHeadshotService.updateHeadshot(actor, headshot, false);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("headshot", ex.getMessage());
            model.addAttribute("commandModel", commandModel);
            UserProjectContext context = resolveUserProjectContext(principal);
            CreateActorViewModel viewModel = actorService.getCreateActorViewModel(commandModel.getProjectIds().isEmpty()
                    ? null
                    : commandModel.getProjectIds().get(0));
            viewModel.setProjects(context.projects().getProjects());
            model.addAttribute("viewModel", viewModel);
            return "actor/create";
        }

        return "redirect:/actor/show?id=" + actor.getId();
    }

    private String renderEditForm(EditActorCommandModel commandModel, Model model, Principal principal) {
        UserProjectContext context = resolveUserProjectContext(principal);
        EditActorViewModel viewModel = actorService.getEditActorViewModel(commandModel.getId());
        viewModel.setProjects(context.projects().getProjects());

        model.addAttribute("viewModel", viewModel);
        model.addAttribute("commandModel", commandModel);

        return "actor/edit";
    }
}
