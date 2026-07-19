package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.ScriptEditionRequests;
import com.scripty.api.SongEditionResource;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.viewmodel.song.edition.SongEditionViewModel;
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
 * Named editions of a song — an alternate lyric, a rewrite, a version cut for a
 * different scene.
 *
 * <p>The song counterpart of {@link ScriptEditionRestController}, filling the
 * same gap: {@code editionId} has been accepted on {@code /api/song/block} and
 * {@code /api/song/version} all along, while nothing said which ids existed.
 *
 * <p>Access is resolved through the owning project, as everywhere else in the
 * song controllers — a document has no permissions of its own.
 *
 * <p>Reuses {@link ScriptEditionRequests} for its bodies. The two kinds of
 * edition take exactly the same fields, and a second pair of identical records
 * would only invite them to drift apart.
 */
@RestController
@RequestMapping("/api/song/edition")
public class SongEditionRestController {

    @Autowired
    SongEditionService songEditionService;

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    ProjectAccessSupport projectAccess;

    private boolean canAccess(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canEdit(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@RequestParam Integer documentId, Principal principal) {
        if (!canAccess(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> create(@RequestParam Integer documentId,
                                    @RequestBody ScriptEditionRequests.Create request,
                                    Principal principal) {
        if (!canEdit(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.name() == null || request.name().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("name", "You must supply a value for Name."), HttpStatus.BAD_REQUEST);
        }
        if (songEditionService.createEdition(documentId, request.name(), request.copyFromEditionId()) == null) {
            return new ResponseEntity<>(
                    Map.of("copyFromEditionId", "That edition is not part of this song."),
                    HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> rename(@PathVariable Integer id,
                                    @RequestParam Integer documentId,
                                    @RequestBody ScriptEditionRequests.Rename request,
                                    Principal principal) {
        if (!canEdit(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.name() == null || request.name().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("name", "You must supply a value for Name."), HttpStatus.BAD_REQUEST);
        }
        if (!songEditionService.renameEdition(id, documentId, request.name())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    /**
     * Deletes an edition and the lyrics written in it. The service refuses to
     * remove the last one, so a song always has somewhere to write.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> delete(@PathVariable Integer id,
                                    @RequestParam Integer documentId,
                                    Principal principal) {
        if (!canEdit(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!songEditionService.deleteEdition(id, documentId)) {
            return new ResponseEntity<>(
                    Map.of("edition", "That edition cannot be deleted."), HttpStatus.CONFLICT);
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    @RequestMapping(value = "/{id}/set-default", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setDefault(@PathVariable Integer id,
                                        @RequestParam Integer documentId,
                                        Principal principal) {
        if (!canEdit(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!songEditionService.setDefaultEdition(id, documentId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    @RequestMapping(value = "/{id}/set-published", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setPublished(@PathVariable Integer id,
                                          @RequestParam Integer documentId,
                                          Principal principal) {
        if (!canEdit(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!songEditionService.setPublishedEdition(id, documentId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    private CollectionModel<EntityModel<SongEditionResource>> collection(
            Integer documentId, Principal principal) {
        boolean canEdit = canEdit(documentId, principal);
        List<SongEditionViewModel> editions =
                songEditionService.getEditionViewModels(documentId, canEdit);

        List<EntityModel<SongEditionResource>> resources = new ArrayList<>();
        if (editions != null) {
            for (SongEditionViewModel edition : editions) {
                resources.add(EntityModel.of(toResource(edition),
                        editionLinks(edition, documentId, canEdit, editions.size())));
            }
        }

        CollectionModel<EntityModel<SongEditionResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(SongEditionRestController.class).list(documentId, null)).withSelfRel())
                .add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.DOCUMENT));
        if (canEdit) {
            collection.add(linkTo(methodOn(SongEditionRestController.class).create(documentId, null, null))
                    .withRel(ApiRel.CREATE));
        }
        return collection;
    }

    private SongEditionResource toResource(SongEditionViewModel edition) {
        SongEditionResource resource = new SongEditionResource();
        resource.setId(edition.getId());
        resource.setName(edition.getName());
        resource.setDefault(edition.isDefault());
        resource.setPublished(edition.isPublished());
        resource.setLastEdited(ApiDates.toOffset(edition.getLastEdited()));
        resource.setBlockCount(edition.getBlockCount());
        return resource;
    }

    private Link[] editionLinks(SongEditionViewModel edition, Integer documentId,
                                boolean canEdit, int total) {
        int id = edition.getId();
        List<Link> links = new ArrayList<>();
        // The lyrics of this edition — the reason for knowing its id.
        links.add(linkTo(methodOn(SongBlockRestController.class).list(documentId, id, null))
                .withRel(ApiRel.SONG_BLOCKS));
        links.add(linkTo(methodOn(SongEditionRestController.class).list(documentId, null))
                .withRel(ApiRel.EDITIONS));
        if (canEdit) {
            links.add(linkTo(methodOn(SongEditionRestController.class).rename(id, documentId, null, null))
                    .withRel(ApiRel.UPDATE));
            if (total > 1) {
                // The last edition cannot go; withholding the link keeps a
                // client from offering an action that can only fail.
                links.add(linkTo(methodOn(SongEditionRestController.class).delete(id, documentId, null))
                        .withRel(ApiRel.DELETE));
            }
            if (!edition.isDefault()) {
                links.add(linkTo(methodOn(SongEditionRestController.class).setDefault(id, documentId, null))
                        .withRel(ApiRel.SET_DEFAULT));
            }
            if (!edition.isPublished()) {
                links.add(linkTo(methodOn(SongEditionRestController.class).setPublished(id, documentId, null))
                        .withRel(ApiRel.SET_PUBLISHED));
            }
        }
        return links.toArray(Link[]::new);
    }
}
