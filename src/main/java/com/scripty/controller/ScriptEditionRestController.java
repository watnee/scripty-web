package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.ScriptEditionRequests;
import com.scripty.api.ScriptEditionResource;
import com.scripty.dto.ScriptEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ScriptEditionService;
import com.scripty.viewmodel.project.edition.ScriptEditionViewModel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Named editions of a screenplay — a shooting draft, a table read, a
 * production revision — over REST.
 *
 * <p>{@code editionId} has been accepted on {@code /api/block},
 * {@code /api/project/{id}/import-script} and {@code /api/project/version} all
 * along, but nothing told a client which ids existed. In the web app the id
 * comes from the session, so the parameter worked there and was unusable from
 * anywhere else. This is the missing half.
 *
 * <p>There is deliberately no {@code switch} endpoint. Switching editions is a
 * session concern the browser needs because its URLs do not carry one; a REST
 * client says which edition it means on every request instead.
 */
@RestController
@RequestMapping("/api/project/edition")
public class ScriptEditionRestController {

    @Autowired
    ScriptEditionService scriptEditionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@RequestParam Integer projectId, Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> create(@RequestParam Integer projectId,
                                    @RequestBody ScriptEditionRequests.Create request,
                                    Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.name() == null || request.name().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("name", "You must supply a value for Name."), HttpStatus.BAD_REQUEST);
        }
        ScriptEdition created = scriptEditionService.createEdition(
                projectId, request.name(), request.copyFromEditionId());
        if (created == null) {
            return new ResponseEntity<>(
                    Map.of("copyFromEditionId", "That edition is not part of this project."),
                    HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> rename(@PathVariable Integer id,
                                    @RequestParam Integer projectId,
                                    @RequestBody ScriptEditionRequests.Rename request,
                                    Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.name() == null || request.name().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("name", "You must supply a value for Name."), HttpStatus.BAD_REQUEST);
        }
        // The service checks the edition really belongs to this project, which
        // is what keeps a caller from renaming someone else's by id.
        if (!scriptEditionService.renameEdition(id, projectId, request.name())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    /**
     * Deletes an edition and everything written in it. The service refuses to
     * remove the last one, so a project always has somewhere to write.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> delete(@PathVariable Integer id,
                                    @RequestParam Integer projectId,
                                    Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!scriptEditionService.deleteEdition(id, projectId)) {
            // Either not in this project, or the only edition left.
            return new ResponseEntity<>(
                    Map.of("edition", "That edition cannot be deleted."), HttpStatus.CONFLICT);
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    /** The edition opened when a request does not name one. */
    @RequestMapping(value = "/{id}/set-default", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setDefault(@PathVariable Integer id,
                                        @RequestParam Integer projectId,
                                        Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!scriptEditionService.setDefaultEdition(id, projectId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    /** The edition view-only readers see, which need not be the default. */
    @RequestMapping(value = "/{id}/set-published", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setPublished(@PathVariable Integer id,
                                          @RequestParam Integer projectId,
                                          Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!scriptEditionService.setPublishedEdition(id, projectId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    private CollectionModel<EntityModel<ScriptEditionResource>> collection(
            Integer projectId, Principal principal) {
        boolean canEdit = projectAccess.canEditScript(projectId, principal);
        // Readers who may not edit the script see only the published edition,
        // which is the same rule the block list resolves access by.
        List<ScriptEditionViewModel> editions =
                scriptEditionService.getEditionViewModels(projectId, canEdit);

        List<EntityModel<ScriptEditionResource>> resources = new ArrayList<>();
        if (editions != null) {
            for (ScriptEditionViewModel edition : editions) {
                resources.add(EntityModel.of(toResource(edition),
                        editionLinks(edition, projectId, canEdit, editions.size())));
            }
        }

        CollectionModel<EntityModel<ScriptEditionResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(ScriptEditionRestController.class).list(projectId, null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        if (canEdit) {
            collection.add(linkTo(methodOn(ScriptEditionRestController.class).create(projectId, null, null))
                    .withRel(ApiRel.CREATE));
        }
        return collection;
    }

    private ScriptEditionResource toResource(ScriptEditionViewModel edition) {
        ScriptEditionResource resource = new ScriptEditionResource();
        resource.setId(edition.getId());
        resource.setName(edition.getName());
        resource.setDefault(edition.isDefault());
        resource.setPublished(edition.isPublished());
        resource.setLastEdited(ApiDates.toOffset(edition.getLastEdited()));
        resource.setBlockCount(edition.getBlockCount());
        return resource;
    }

    private Link[] editionLinks(ScriptEditionViewModel edition, Integer projectId,
                                boolean canEdit, int total) {
        int id = edition.getId();
        List<Link> links = new ArrayList<>();
        // The blocks of this edition — the whole point of knowing its id.
        links.add(linkTo(methodOn(BlockRestController.class).list(projectId, id, null))
                .withRel(ApiRel.BLOCKS));
        links.add(linkTo(methodOn(ScriptEditionRestController.class).list(projectId, null))
                .withRel(ApiRel.EDITIONS));
        if (canEdit) {
            links.add(linkTo(methodOn(ScriptEditionRestController.class).rename(id, projectId, null, null))
                    .withRel(ApiRel.UPDATE));
            if (total > 1) {
                // The last edition cannot go; not advertising it saves the
                // client from offering an action that can only fail.
                links.add(linkTo(methodOn(ScriptEditionRestController.class).delete(id, projectId, null))
                        .withRel(ApiRel.DELETE));
            }
            if (!edition.isDefault()) {
                links.add(linkTo(methodOn(ScriptEditionRestController.class).setDefault(id, projectId, null))
                        .withRel(ApiRel.SET_DEFAULT));
            }
            if (!edition.isPublished()) {
                links.add(linkTo(methodOn(ScriptEditionRestController.class).setPublished(id, projectId, null))
                        .withRel(ApiRel.SET_PUBLISHED));
            }
        }
        return links.toArray(Link[]::new);
    }
}
