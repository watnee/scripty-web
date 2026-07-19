package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.DeletedBlockResource;
import com.scripty.dto.Block;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockTrashService;
import com.scripty.viewmodel.block.trash.DeletedBlockListViewModel;
import com.scripty.viewmodel.block.trash.DeletedBlockViewModel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Recovery for deleted screenplay elements — the REST counterpart of the web
 * editor's block trash.
 *
 * <p>{@code DELETE /api/block/{id}} has always put an element here; until now
 * an API client had no way to get it back out, which made every delete final
 * from the iPad and reversible from the browser.
 *
 * <p>Every lookup goes through the service with both the project id and the
 * current user, never by deleted-block id alone. The trash bypasses the
 * soft-delete restriction that normally scopes queries, so resolving by id
 * would let a caller probe for elements in projects they cannot see.
 */
@RestController
@RequestMapping("/api/block/trash")
public class BlockTrashRestController {

    @Autowired
    BlockTrashService blockTrashService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@RequestParam Integer projectId, Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    /**
     * Puts an element back where it was. The restored element is a new resource
     * with a new id — see {@code DeletedBlock} for why the original cannot
     * return — so the refreshed trash is returned rather than the block.
     */
    @RequestMapping(value = "/{id}/restore", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> restore(@PathVariable Integer id,
                                     @RequestParam Integer projectId,
                                     Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = projectAccess.currentUser(principal);
        Block restored = blockTrashService.restore(id, projectId, user);
        if (restored == null) {
            // Already restored, already purged, or never in this project's
            // trash. All three are "not here", and telling them apart would
            // leak whether the id exists elsewhere.
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    /** Deletes an element from the trash for good. */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> purge(@PathVariable Integer id,
                                   @RequestParam Integer projectId,
                                   Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = projectAccess.currentUser(principal);
        if (!blockTrashService.purge(id, projectId, user)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    private CollectionModel<EntityModel<DeletedBlockResource>> collection(
            Integer projectId, Principal principal) {
        User user = projectAccess.currentUser(principal);
        DeletedBlockListViewModel viewModel = blockTrashService.getTrashViewModel(projectId, user);

        List<EntityModel<DeletedBlockResource>> resources = new ArrayList<>();
        if (viewModel != null && viewModel.getBlocks() != null) {
            for (DeletedBlockViewModel block : viewModel.getBlocks()) {
                resources.add(EntityModel.of(toResource(block), itemLinks(block.getId(), projectId)));
            }
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(BlockTrashRestController.class).list(projectId, null)).withSelfRel())
                .add(linkTo(methodOn(BlockRestController.class).list(projectId, null, null)).withRel(ApiRel.BLOCKS))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
    }

    private DeletedBlockResource toResource(DeletedBlockViewModel block) {
        DeletedBlockResource resource = new DeletedBlockResource();
        resource.setId(block.getId());
        resource.setPreview(block.getPreview());
        resource.setEmpty(block.isEmpty());
        resource.setTypeLabel(block.getTypeLabel());
        resource.setEditionName(block.getEditionName());
        resource.setDeletedByName(block.getDeletedByName());
        resource.setDeletedAt(ApiDates.toOffset(block.getDeletedAt()));
        resource.setPurgeAt(ApiDates.toOffset(block.getPurgeAt()));
        return resource;
    }

    private Link[] itemLinks(int id, Integer projectId) {
        return new Link[]{
                linkTo(methodOn(BlockTrashRestController.class).restore(id, projectId, null))
                        .withRel(ApiRel.RESTORE),
                linkTo(methodOn(BlockTrashRestController.class).purge(id, projectId, null))
                        .withRel(ApiRel.PURGE),
                linkTo(methodOn(BlockTrashRestController.class).list(projectId, null))
                        .withRel(ApiRel.TRASH)
        };
    }
}
