package com.scripty.controller;

import com.scripty.api.ProjectResource;
import com.scripty.api.ProjectResourceAssembler;
import com.scripty.api.RestErrors;
import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectService;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/project")
public class ProjectRestController {

    @Autowired
    ProjectService projectService;

    @Autowired
    ProjectResourceAssembler projectResourceAssembler;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<ProjectResource>>> list(Principal principal) {
        User user = projectAccess.currentUser(principal);
        ProjectListViewModel viewModel;
        if (user != null
                && !user.isAdmin() && !user.isDirector() && !user.isProducer()
                && !user.isWriter() && !user.isActor() && !user.isCrew()
                && !user.isDirectorOfPhotography() && !user.isCastingDirector()) {
            viewModel = projectService.getProjectListViewModel(user.getTeam());
        } else {
            viewModel = projectService.getProjectListViewModel();
        }
        return ResponseEntity.ok(projectResourceAssembler.toProjectCollection(viewModel.getProjects()));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateProjectCommandModel commandModel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        Project project = projectService.saveCreateProjectCommandModel(commandModel);
        EntityModel<ProjectResource> resource = projectResourceAssembler.toModel(project);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<ProjectResource>> show(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessProject(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(projectResourceAssembler.toModel(viewModel));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody EditProjectCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canAccessProject(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        commandModel.setId(id);
        Project project = projectService.saveEditProjectCommandModel(commandModel);
        return ResponseEntity.ok(projectResourceAssembler.toModel(project));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<ProjectResource>> delete(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessProject(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Project project = projectService.deleteProject(id);
        return ResponseEntity.ok(projectResourceAssembler.toDeleteModel(project));
    }
}
