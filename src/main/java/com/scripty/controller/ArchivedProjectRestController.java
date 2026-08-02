package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.ArchivedProjectResource;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * The project archive: screenplays put aside without being deleted.
 *
 * <p>The document archive one level up. Deliberately not the trash: nothing here
 * is on a clock, an archived project is still whole — openable, editable,
 * exportable, and still in a whole-project bundle export — and the way back has
 * no "restore" overtones. Archiving itself lives beside delete on
 * {@link ProjectRestController}, the same split the trash uses.
 *
 * <p>Named for what it holds rather than as {@code ProjectArchiveRestController},
 * because {@code ProjectArchiveService} is the unrelated {@code .scripty.json}
 * bundle export and one of those confusions per codebase is enough.
 *
 * <p>Unlike the trash, this needs no native queries: an archived project is
 * visible to ordinary JPQL with its teams loaded, so lookups go through the
 * user-scoped {@code getArchivedProject(id, user)} and the one access rule.
 */
@RestController
@RequestMapping("/api/project/archive")
public class ArchivedProjectRestController {

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

    /** Brings a screenplay back into the project list. */
    @RequestMapping(value = "/{id}/unarchive", method = RequestMethod.POST,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> unarchive(@PathVariable Integer id, Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Confirm it is an archived project this user can reach before acting,
        // so the id-only service call below cannot touch anyone else's.
        if (projectService.getArchivedProject(id, user) == null) {
            return ResponseEntity.notFound().build();
        }
        if (projectService.unarchiveProject(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(user));
    }

    /**
     * Brings several screenplays back into the list in one call.
     *
     * <p>There is no bulk archive to mirror one level up — screenplays are put
     * aside one production at a time, as they finish — but coming back is the
     * other way round: a writer opening this sheet after a season is looking at
     * a shelf, and wants a handful of it back at once. Ids that are not archived
     * projects this user can reach are skipped.
     */
    @RequestMapping(value = "/bulk/unarchive", method = RequestMethod.POST,
            consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> bulkUnarchive(@RequestBody(required = false) BulkUnarchiveProjectsRequest request,
                                           Principal principal) {
        User user = projectAccess.currentUser(principal);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            return new ResponseEntity<>(
                    Map.of("ids", "Choose at least one screenplay to bring back."),
                    HttpStatus.BAD_REQUEST);
        }
        if (projectService.unarchiveProjects(request.ids(), user) == 0) {
            return new ResponseEntity<>(
                    Map.of("ids", "Those screenplays could not be brought back."),
                    HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(collection(user));
    }

    /** The ticked ids, in the order the archive showed them. */
    public record BulkUnarchiveProjectsRequest(List<Integer> ids) {
    }

    private CollectionModel<EntityModel<ArchivedProjectResource>> collection(User user) {
        List<EntityModel<ArchivedProjectResource>> resources = new ArrayList<>();
        for (Project project : projectService.getArchivedProjects(user)) {
            resources.add(EntityModel.of(toResource(project), itemLinks(project.getId())));
        }
        CollectionModel<EntityModel<ArchivedProjectResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(ArchivedProjectRestController.class).list(null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS));
        // Only worth offering when there is something here to tick.
        if (!resources.isEmpty()) {
            collection.add(linkTo(methodOn(ArchivedProjectRestController.class)
                    .bulkUnarchive(null, null)).withRel(ApiRel.BULK_UNARCHIVE));
        }
        return collection;
    }

    private ArchivedProjectResource toResource(Project project) {
        ArchivedProjectResource resource = new ArchivedProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        resource.setLastEdited(ApiDates.toOffset(project.getLastEdited()));
        resource.setArchivedAt(ApiDates.toOffset(project.getArchivedAt()));
        resource.setTeams(project.getTeamNames());
        return resource;
    }

    private Link[] itemLinks(int id) {
        return new Link[]{
                linkTo(methodOn(ArchivedProjectRestController.class).unarchive(id, null))
                        .withRel(ApiRel.UNARCHIVE),
                // Unlike a trashed project, an archived one can still be opened
                // and worked on in place, so say where it lives.
                linkTo(methodOn(ProjectRestController.class).show(id, null))
                        .withRel(ApiRel.PROJECT),
                // Deciding an archived production is finished with for good
                // should not mean unarchiving it first. This is the ordinary
                // soft delete, so it still lands in the trash.
                linkTo(methodOn(ProjectRestController.class).delete(id, null))
                        .withRel(ApiRel.DELETE),
                linkTo(methodOn(ArchivedProjectRestController.class).list(null))
                        .withRel(ApiRel.ARCHIVED)
        };
    }
}
