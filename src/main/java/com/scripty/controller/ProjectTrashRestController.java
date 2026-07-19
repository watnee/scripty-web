package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.TrashedProjectResource;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Recovery for deleted screenplays.
 *
 * <p>{@code DELETE /api/project/{id}} has always been a soft delete, but with
 * nothing to read the trash back out an API client could only ever destroy a
 * whole screenplay irrecoverably. This is the way back.
 *
 * <p>Everything resolves through the user-scoped service lookups
 * ({@code getTrashedProject(id, user)}), never by id alone. Trashed rows sit
 * outside the soft-delete restriction that normally scopes queries, so an
 * id-only lookup would let a caller confirm the existence of other people's
 * projects.
 */
@RestController
@RequestMapping("/api/project/trash")
public class ProjectTrashRestController {

    @Autowired
    ProjectService projectService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(user));
    }

    /** Brings a screenplay back out of the trash, with everything in it. */
    @RequestMapping(value = "/{id}/restore", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> restore(@PathVariable Integer id, Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Confirm it is this user's trashed project before acting, so the
        // id-only service call below cannot reach anyone else's.
        if (projectService.getTrashedProject(id, user) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!projectService.restoreProject(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(user));
    }

    /** Destroys a screenplay for good. There is nothing after this. */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> purge(@PathVariable Integer id, Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (projectService.getTrashedProject(id, user) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!projectService.purgeProject(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(user));
    }

    /** Destroys everything in the trash. Scoped to the caller's own projects. */
    @RequestMapping(method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> empty(Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        projectService.emptyTrash(user);
        return ResponseEntity.ok(collection(user));
    }

    private CollectionModel<EntityModel<TrashedProjectResource>> collection(User user) {
        List<EntityModel<TrashedProjectResource>> resources = new ArrayList<>();
        for (Project project : projectService.getTrashedProjects(user)) {
            resources.add(EntityModel.of(toResource(project), itemLinks(project.getId())));
        }
        CollectionModel<EntityModel<TrashedProjectResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(ProjectTrashRestController.class).list(null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS));
        if (!resources.isEmpty()) {
            // Nothing to empty when the trash is already empty.
            collection.add(linkTo(methodOn(ProjectTrashRestController.class).empty(null))
                    .withRel(ApiRel.EMPTY_TRASH));
        }
        return collection;
    }

    private TrashedProjectResource toResource(Project project) {
        TrashedProjectResource resource = new TrashedProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        resource.setDeletedAt(ApiDates.toOffset(project.getDeletedAt()));
        return resource;
    }

    private Link[] itemLinks(int id) {
        return new Link[]{
                linkTo(methodOn(ProjectTrashRestController.class).restore(id, null))
                        .withRel(ApiRel.RESTORE),
                linkTo(methodOn(ProjectTrashRestController.class).purge(id, null))
                        .withRel(ApiRel.PURGE),
                linkTo(methodOn(ProjectTrashRestController.class).list(null))
                        .withRel(ApiRel.TRASH)
        };
    }
}
