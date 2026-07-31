package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.ArchivedDocumentResource;
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
 * The archive: songs and notes a writer has put aside without deleting.
 *
 * <p>Deliberately not the trash. Nothing here is on a clock, an archived
 * document is still whole and openable, and the way back has no "restore"
 * overtones — so it gets its own collection rather than a flag on
 * {@link DocumentTrashRestController}. Archiving itself lives beside delete on
 * {@link TextDocumentRestController}, the same split the trash uses.
 *
 * <p>As with the trash, lookups go through the service with the project id and
 * the current user rather than by document id alone, since archived rows sit
 * outside the query that normally scopes a project's list.
 */
@RestController
@RequestMapping("/api/document/archive")
public class DocumentArchiveRestController {

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

    @RequestMapping(value = "/{id}/unarchive", method = RequestMethod.POST,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> unarchive(@PathVariable Integer id,
                                       @RequestParam Integer projectId,
                                       Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = projectAccess.currentUser(principal);
        if (textDocumentService.unarchive(id, projectId, user) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    private CollectionModel<EntityModel<ArchivedDocumentResource>> collection(
            Integer projectId, Principal principal) {
        User user = projectAccess.currentUser(principal);
        TextDocumentListViewModel viewModel = textDocumentService.getArchiveViewModel(projectId, user);

        List<EntityModel<ArchivedDocumentResource>> resources = new ArrayList<>();
        if (viewModel != null) {
            // The view model keeps songs and drafts apart for two web tabs;
            // over the API they are one archive, told apart by documentType.
            addAll(resources, viewModel.getSongs(), projectId);
            addAll(resources, viewModel.getDrafts(), projectId);
        }

        return CollectionModel.of(resources)
                .add(linkTo(methodOn(DocumentArchiveRestController.class).list(projectId, null)).withSelfRel())
                .add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, null, null))
                        .withRel(ApiRel.DOCUMENTS))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
    }

    private void addAll(List<EntityModel<ArchivedDocumentResource>> into,
                        List<TextDocumentViewModel> documents,
                        Integer projectId) {
        if (documents == null) {
            return;
        }
        for (TextDocumentViewModel document : documents) {
            into.add(EntityModel.of(toResource(document), itemLinks(document.getId(), projectId)));
        }
    }

    private ArchivedDocumentResource toResource(TextDocumentViewModel document) {
        ArchivedDocumentResource resource = new ArchivedDocumentResource();
        resource.setId(document.getId());
        resource.setTitle(document.getTitle());
        resource.setDocumentType(document.getDocumentType());
        resource.setDocumentTypeLabel(document.getDocumentTypeLabel());
        resource.setPreview(document.getPreview());
        resource.setArchivedAt(ApiDates.toOffset(document.getArchivedAt()));
        return resource;
    }

    private Link[] itemLinks(int id, Integer projectId) {
        return new Link[]{
                linkTo(methodOn(DocumentArchiveRestController.class).unarchive(id, projectId, null))
                        .withRel(ApiRel.UNARCHIVE),
                // Unlike a trashed document, an archived one can still be opened
                // and read in place, so say where it lives.
                linkTo(methodOn(TextDocumentRestController.class).show(id, null))
                        .withRel(ApiRel.DOCUMENT),
                // Deciding an archived piece is finished with for good should not
                // mean unarchiving it first. This is the ordinary soft delete, so
                // it still lands in the trash — matching the web archive page.
                linkTo(methodOn(TextDocumentRestController.class).delete(id, projectId, null))
                        .withRel(ApiRel.DELETE),
                linkTo(methodOn(DocumentArchiveRestController.class).list(projectId, null))
                        .withRel(ApiRel.ARCHIVED)
        };
    }
}
