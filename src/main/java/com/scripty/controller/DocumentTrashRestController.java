package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.DeletedDocumentResource;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.TextDocumentService;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
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
 * Recovery for deleted songs and notes — the last of the three trashes to reach
 * the API, after elements and screenplays.
 *
 * <p>{@code DELETE /api/document/{id}} has always been a soft delete; without
 * this a client could put a song out of reach and never get it back.
 *
 * <p>As with the other two, lookups go through the service with the project id
 * and the current user rather than by document id alone, since trashed rows sit
 * outside the restriction that normally scopes queries.
 */
@RestController
@RequestMapping("/api/document/trash")
public class DocumentTrashRestController {

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@RequestParam Integer projectId, Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    @RequestMapping(value = "/{id}/restore", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> restore(@PathVariable Integer id,
                                     @RequestParam Integer projectId,
                                     Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = projectAccess.currentUser(principal);
        if (textDocumentService.restore(id, projectId, user) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> purge(@PathVariable Integer id,
                                   @RequestParam Integer projectId,
                                   Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = projectAccess.currentUser(principal);
        if (!textDocumentService.purge(id, projectId, user)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    private CollectionModel<EntityModel<DeletedDocumentResource>> collection(
            Integer projectId, Principal principal) {
        User user = projectAccess.currentUser(principal);
        TextDocumentListViewModel viewModel = textDocumentService.getTrashViewModel(projectId, user);

        List<EntityModel<DeletedDocumentResource>> resources = new ArrayList<>();
        if (viewModel != null) {
            // The view model keeps songs and drafts apart for two web tabs;
            // over the API they are one trash, told apart by documentType.
            addAll(resources, viewModel.getSongs(), projectId);
            addAll(resources, viewModel.getDrafts(), projectId);
        }

        return CollectionModel.of(resources)
                .add(linkTo(methodOn(DocumentTrashRestController.class).list(projectId, null)).withSelfRel())
                .add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, null, null))
                        .withRel(ApiRel.DOCUMENTS))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
    }

    private void addAll(List<EntityModel<DeletedDocumentResource>> into,
                        List<TextDocumentViewModel> documents,
                        Integer projectId) {
        if (documents == null) {
            return;
        }
        for (TextDocumentViewModel document : documents) {
            into.add(EntityModel.of(toResource(document), itemLinks(document.getId(), projectId)));
        }
    }

    private DeletedDocumentResource toResource(TextDocumentViewModel document) {
        DeletedDocumentResource resource = new DeletedDocumentResource();
        resource.setId(document.getId());
        resource.setTitle(document.getTitle());
        resource.setDocumentType(document.getDocumentType());
        resource.setDocumentTypeLabel(document.getDocumentTypeLabel());
        resource.setPreview(document.getPreview());
        resource.setDeletedAt(ApiDates.toOffset(document.getDeletedAt()));
        resource.setPurgesAt(ApiDates.toOffset(document.getPurgesAt()));
        return resource;
    }

    private Link[] itemLinks(int id, Integer projectId) {
        return new Link[]{
                linkTo(methodOn(DocumentTrashRestController.class).restore(id, projectId, null))
                        .withRel(ApiRel.RESTORE),
                linkTo(methodOn(DocumentTrashRestController.class).purge(id, projectId, null))
                        .withRel(ApiRel.PURGE),
                linkTo(methodOn(DocumentTrashRestController.class).list(projectId, null))
                        .withRel(ApiRel.TRASH)
        };
    }
}
