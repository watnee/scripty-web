package com.scripty.controller;

import com.scripty.api.ApiRel;
import com.scripty.api.ProjectAccessUserResource;
import com.scripty.api.ProjectResource;
import com.scripty.api.ProjectResourceAssembler;
import com.scripty.api.RestErrors;
import com.scripty.commandmodel.project.createproject.CreateProjectCommandModel;
import com.scripty.commandmodel.project.editproject.EditProjectCommandModel;
import com.scripty.commandmodel.project.titlepage.TitlePageCommandModel;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.FountainImportService;
import com.scripty.service.ProjectArchiveService;
import com.scripty.service.ScriptEditionService;
import com.scripty.service.ScriptImportException;
import com.scripty.service.ProjectService;
import com.scripty.service.UserService;
import com.scripty.viewmodel.project.projectlist.ProjectListViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectShareUserViewModel;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(value = "/api/project")
public class ProjectRestController {

    @Autowired
    ProjectService projectService;

    @Autowired
    ProjectResourceAssembler projectResourceAssembler;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    UserService userService;

    @Autowired
    ProjectArchiveService projectArchiveService;

    @Autowired
    FountainImportService fountainImportService;

    @Autowired
    ScriptEditionService scriptEditionService;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
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
        Integer defaultProjectId = user != null ? user.getDefaultProjectId() : null;
        return ResponseEntity.ok(
                projectResourceAssembler.toProjectCollection(viewModel.getProjects(), defaultProjectId));
    }

    /**
     * Toggles this project as the current user's default (mirrors the web
     * list's star). Returns the refreshed project collection so the caller
     * sees the updated default flags.
     */
    @RequestMapping(value = "/{id}/toggleDefault", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<ProjectResource>>> toggleDefault(
            @PathVariable Integer id, Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (id.equals(user.getDefaultProjectId())) {
            user.setDefaultProjectId(null);
        } else if (projectAccess.canAccessProject(id, user)) {
            user.setDefaultProjectId(id);
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.update(user);
        return list(principal);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
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

    /**
     * Imports one or more projects from a .scripty.json archive (mirrors the web
     * list's Import button). The uploaded file is the "file" multipart part. A
     * multi-project bundle returns a collection instead of a single resource,
     * since there is no one Location to point at.
     */
    @RequestMapping(value = "/import", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> importProject(@RequestPart("file") MultipartFile file) {
        try {
            List<Project> projects = projectArchiveService.importProjects(file);
            if (projects.size() == 1) {
                EntityModel<ProjectResource> resource = projectResourceAssembler.toModel(projects.get(0));
                return ResponseEntity
                        .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                        .body(resource);
            }
            List<EntityModel<ProjectResource>> resources = projects.stream()
                    .map(projectResourceAssembler::toModel)
                    .toList();
            return ResponseEntity.status(HttpStatus.CREATED).body(CollectionModel.of(resources));
        } catch (ScriptImportException e) {
            return new ResponseEntity<>(java.util.Map.of("file", e.getUserMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
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

    /**
     * Everyone who can already see this project, and whether they can write.
     *
     * <p>The invitation list answers a different question. Role and team
     * membership grant access on their own, so a project can be readable by
     * people no invitation names — and the web project page has always shown
     * this list beside the invitations for exactly that reason.
     *
     * <p>Access to the project is enough to read it: knowing who else is in the
     * room is part of working in it, and none of it is hidden from these people
     * anywhere else in the app.
     */
    @RequestMapping(value = "/{id}/access", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> access(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessProject(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (projectService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        List<EntityModel<ProjectAccessUserResource>> people = new ArrayList<>();
        for (ProjectShareUserViewModel user : projectService.getProjectShareAccessUsers(id)) {
            ProjectAccessUserResource resource = new ProjectAccessUserResource();
            resource.setDisplayName(user.getDisplayName());
            resource.setAccessLabel(user.getAccessLabel());
            resource.setCanEdit(user.isCanEdit());
            resource.setPermissionLabel(user.getPermissionLabel());
            people.add(EntityModel.of(resource));
        }
        return ResponseEntity.ok(CollectionModel.of(people)
                .add(linkTo(methodOn(ProjectRestController.class).access(id, null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).show(id, null)).withRel(ApiRel.PROJECT)));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
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
        // Title-page edits are a screenplay edit, gated the same way the MVC
        // /project/titlePage handler gates them.
        if (commandModel.hasTitlePageFields() && !projectAccess.canEditScript(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Project project = projectService.saveEditProjectCommandModel(commandModel);
        if (commandModel.hasTitlePageFields()) {
            project = saveTitlePageFields(id, commandModel);
        }
        return ResponseEntity.ok(projectResourceAssembler.toModel(project));
    }

    /**
     * Persists the optional title-page fields through the same service path as
     * the web title-page form. Starts from the stored values so a field the
     * caller omitted stays untouched.
     */
    private Project saveTitlePageFields(Integer id, EditProjectCommandModel commandModel) {
        TitlePageCommandModel titlePage = projectService.getTitlePageCommandModel(id);
        if (titlePage == null) {
            return projectService.read(id);
        }
        if (commandModel.getScreenplayTitle() != null) {
            titlePage.setScreenplayTitle(commandModel.getScreenplayTitle());
        }
        if (commandModel.getWriters() != null) {
            titlePage.setWriters(commandModel.getWriters());
        }
        if (commandModel.getContactInfo() != null) {
            titlePage.setContactInfo(commandModel.getContactInfo());
        }
        if (commandModel.getScreenplayVersion() != null) {
            titlePage.setScreenplayVersion(commandModel.getScreenplayVersion());
        }
        return projectService.saveTitlePageCommandModel(titlePage);
    }

    /**
     * Replaces the project's script from an uploaded file (mirrors the web
     * editor's Import button). Accepts .fountain, .txt, .docx, .doc, .fdx and
     * .pdf as the "file" multipart part; an unsupported or unreadable file comes
     * back as a validation error rather than a failure.
     */
    @RequestMapping(value = "/{id}/import-script", method = RequestMethod.POST,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> importScript(
            @PathVariable Integer id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (projectService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!projectAccess.canEditScript(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ScriptEdition edition = scriptEditionService.requireForProject(id, editionId);
        Integer resolvedEditionId = edition != null ? edition.getId() : editionId;
        try {
            FountainImportService.ImportOutcome outcome =
                    fountainImportService.importFileIntoProjectWithStatus(id, resolvedEditionId, file);
            if (!outcome.success()) {
                return new ResponseEntity<>(java.util.Map.of("file", outcome.message()), HttpStatus.BAD_REQUEST);
            }
        } catch (ScriptImportException e) {
            return new ResponseEntity<>(java.util.Map.of("file", e.getUserMessage()), HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            return new ResponseEntity<>(java.util.Map.of("file",
                    "Could not import that file. Check access and try a .fountain, .txt, .docx, .doc, .fdx, or .pdf file."),
                    HttpStatus.BAD_REQUEST);
        }
        ProjectProfileViewModel viewModel = projectService.getProjectProfileViewModel(id);
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(projectResourceAssembler.toModel(viewModel));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<ProjectResource>> delete(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessProject(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Project project = projectService.deleteProject(id);
        return ResponseEntity.ok(projectResourceAssembler.toDeleteModel(project));
    }
}
