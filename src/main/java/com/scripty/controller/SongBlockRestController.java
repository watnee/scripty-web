package com.scripty.controller;

import com.scripty.api.CreateSongBlockBelowRequest;
import com.scripty.api.EditSongBlockRequest;
import com.scripty.api.MoveBlockRequest;
import com.scripty.api.SetSongBlockHighlightRequest;
import com.scripty.api.SongBlockResource;
import com.scripty.api.SongBlockResourceAssembler;
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import java.security.Principal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST/HAL counterpart of {@link SongBlockController}, mirroring
 * {@link BlockRestController} for songs. Reuses {@link SongBlockService} so
 * behaviour (content resync, undo checkpoints, version auto-save) stays
 * identical to the web editor. Reads require project access; every mutation
 * requires script edit permission.
 */
@RestController
@RequestMapping(value = "/api/song/block")
public class SongBlockRestController {

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    SongEditionService songEditionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    SongUndoRedoService songUndoRedoService;

    @Autowired
    SongVersionService songVersionService;

    @Autowired
    SongBlockResourceAssembler assembler;

    // Song editing follows the same rule as the screenplay: writer (or admin)
    // permission on a project the user can access. ProjectAccessSupport's own
    // canEditBlock resolves screenplay blocks, so song block ids are mapped to a
    // project here before the check — see SongBlockController.
    private boolean canEditBlock(Integer blockId, Principal principal) {
        Integer projectId = songBlockService.projectIdForBlock(blockId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    private boolean canEditDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    private boolean canAccessDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    /** Resolves a missing editionId to the song's default version (back-compat). */
    private Integer effectiveEdition(Integer documentId, Integer editionId) {
        SongEdition edition = songEditionService.requireForDocument(documentId, editionId);
        if (edition == null) {
            edition = songEditionService.ensureDefaultEdition(documentId);
        }
        return edition != null ? edition.getId() : editionId;
    }

    /** The song's lyric lines, in order. */
    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<SongBlockResource>>> list(
            @RequestParam Integer documentId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        Integer resolved = effectiveEdition(documentId, editionId);
        return ResponseEntity.ok(assembler.toCollection(
                songBlockService.getBlocks(documentId, resolved), documentId, projectId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<SongBlockResource>> show(@PathVariable Integer id, Principal principal) {
        Integer projectId = songBlockService.projectIdForBlock(id);
        if (projectId == null || !projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SongBlock block = songBlockService.read(id);
        if (block == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toModel(block, projectId));
    }

    /** Appends a new empty line at the end of the song. */
    @RequestMapping(method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> append(@RequestParam Integer documentId,
                                    @RequestParam(required = false) Integer editionId,
                                    Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        songUndoRedoService.recordCheckpoint(documentId, resolved);
        SongBlock created = songBlockService.appendBlock(documentId, resolved);
        if (created == null) {
            return ResponseEntity.notFound().build();
        }
        songVersionService.autoSaveVersion(documentId, resolved);
        return created(created, songBlockService.projectIdForDocument(documentId));
    }

    /**
     * Inserts a line directly below {@code id}, the way pressing Enter does in
     * the web editor. Unlike the HTMX endpoint, the posted content is the new
     * line's — the line being split is left untouched.
     */
    @RequestMapping(value = "/{id}/below", method = RequestMethod.POST,
            consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> createBelow(
            @PathVariable Integer id,
            @RequestBody(required = false) CreateSongBlockBelowRequest request,
            Principal principal) {
        if (!canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        // Null afterContent leaves the origin line as it is; the request body's
        // content belongs to the new line below it.
        SongBlock created = songBlockService.createBelow(id, null);
        if (created == null) {
            return ResponseEntity.notFound().build();
        }
        String content = request != null ? request.content() : null;
        if (content != null && !content.isEmpty()) {
            created = songBlockService.editContent(created.getId(), content);
        }
        Integer documentId = songBlockService.documentIdForBlock(id);
        songVersionService.autoSaveVersion(documentId, songBlockService.editionIdForBlock(id));
        return created(created, songBlockService.projectIdForDocument(documentId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT,
            consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @RequestBody EditSongBlockRequest request,
            Principal principal) {
        if (!canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        String content = request != null && request.content() != null ? request.content() : "";
        SongBlock block = songBlockService.editContent(id, content);
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.ok(assembler.toModel(block, songBlockService.projectIdForBlock(id)));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> delete(@PathVariable Integer id, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Resolve the project and version before the block is gone.
        Integer projectId = songBlockService.projectIdForBlock(id);
        Integer editionId = songBlockService.editionIdForBlock(id);
        songUndoRedoService.recordCheckpointForBlock(id);
        Integer documentId = songBlockService.deleteBlock(id);
        if (documentId == null) {
            return ResponseEntity.notFound().build();
        }
        songVersionService.autoSaveVersion(documentId, editionId);
        return ResponseEntity.ok(assembler.toDeleteModel(documentId, projectId));
    }

    /** Sets the background tint on a line; an unknown or blank color clears it. */
    @RequestMapping(value = "/{id}/highlight", method = RequestMethod.POST,
            consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setHighlight(
            @PathVariable Integer id,
            @RequestBody(required = false) SetSongBlockHighlightRequest request,
            Principal principal) {
        if (!canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        SongBlock block = songBlockService.setHighlight(id, request != null ? request.highlight() : null);
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.ok(assembler.toModel(block, songBlockService.projectIdForBlock(id)));
    }

    /**
     * Reorders a line to an absolute {@code position}, matching the order values
     * reported by the block collection.
     */
    @RequestMapping(value = "/{id}/move", method = RequestMethod.POST,
            consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> move(
            @PathVariable Integer id,
            @RequestBody MoveBlockRequest request,
            Principal principal) {
        if (!canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null || request.position() == null) {
            return new ResponseEntity<>(Map.of("position", "You must supply a value for Position."),
                    HttpStatus.BAD_REQUEST);
        }
        SongBlock current = songBlockService.read(id);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        Integer projectId = songBlockService.projectIdForBlock(id);
        if (request.position().equals(current.getOrder())) {
            return ResponseEntity.ok(assembler.toModel(current, projectId));
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        SongBlock block = songBlockService.moveTo(id, request.position());
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.ok(assembler.toModel(block, projectId));
    }

    private ResponseEntity<EntityModel<SongBlockResource>> created(SongBlock block, Integer projectId) {
        EntityModel<SongBlockResource> resource = assembler.toModel(block, projectId);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }
}
