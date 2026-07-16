package com.scripty.controller;

import com.scripty.api.CreateSongBlockBelowRequest;
import com.scripty.api.EditSongBlockRequest;
import com.scripty.api.MoveSongBlockRequest;
import com.scripty.api.SetSongBlockHighlightRequest;
import com.scripty.api.SongBlockResource;
import com.scripty.api.SongBlockResourceAssembler;
import com.scripty.dto.SongBlock;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import java.security.Principal;
import java.util.LinkedHashMap;
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
 * REST/HAL counterpart of {@link SongBlockController}: manages a song's ordered
 * lyric lines for the iPad client. Reuses {@link SongBlockService}, so
 * behaviour (parent document content stays in sync, undo checkpoints, version
 * auto-save) is identical to the web editor.
 *
 * <p>Access follows the song editor's rule — any project member may edit —
 * unlike {@link BlockRestController}, where screenplay blocks need script write
 * access.
 */
@RestController
@RequestMapping(value = "/api/song/block")
public class SongBlockRestController {

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    SongUndoRedoService songUndoRedoService;

    @Autowired
    SongVersionService songVersionService;

    @Autowired
    SongBlockResourceAssembler assembler;

    @Autowired
    ProjectAccessSupport projectAccess;

    private boolean canAccessBlock(Integer blockId, Principal principal) {
        Integer projectId = songBlockService.projectIdForBlock(blockId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canAccessDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    /** The song's lines, in order. Seeds them from the document's text on first read. */
    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<SongBlockResource>>> list(
            @RequestParam Integer documentId,
            Principal principal) {
        if (songBlockService.projectIdForDocument(documentId) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(assembler.toCollection(songBlockService.getBlocks(documentId), documentId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<SongBlockResource>> show(@PathVariable Integer id, Principal principal) {
        SongBlock block = songBlockService.read(id);
        if (block == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(assembler.toModel(block, songBlockService.documentIdForBlock(id)));
    }

    /** Appends a new empty line at the end of the song. */
    @RequestMapping(method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> append(@RequestParam Integer documentId, Principal principal) {
        if (songBlockService.projectIdForDocument(documentId) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.recordCheckpoint(documentId);
        SongBlock created = songBlockService.appendBlock(documentId);
        if (created == null) {
            return ResponseEntity.notFound().build();
        }
        songVersionService.autoSaveVersion(documentId);
        return created(assembler.toModel(created, documentId));
    }

    /** Persists new text on a line. Content may be empty. */
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @RequestBody EditSongBlockRequest request,
            Principal principal) {
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        String content = request != null && request.content() != null ? request.content() : "";
        SongBlock block = songBlockService.editContent(id, content);
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.ok(assembler.toModel(block, songBlockService.documentIdForBlock(id)));
    }

    /**
     * Deletes a line. A song always keeps at least one (empty) line, so deleting
     * the last one clears it rather than removing it.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> delete(@PathVariable Integer id, Principal principal) {
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        Integer documentId = songBlockService.deleteBlock(id);
        songVersionService.autoSaveVersion(documentId);
        return ResponseEntity.ok(assembler.toDeleteModel(documentId));
    }

    /**
     * Inserts an empty line directly below {@code id}, the way pressing Enter
     * does in the web editor. {@code afterContent} commits the anchor line's
     * text in the same call.
     */
    @RequestMapping(value = "/{id}/below", method = RequestMethod.POST,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> createBelow(
            @PathVariable Integer id,
            @RequestBody(required = false) CreateSongBlockBelowRequest request,
            Principal principal) {
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        SongBlock created = songBlockService.createBelow(id, request != null ? request.afterContent() : null);
        if (created == null) {
            return ResponseEntity.notFound().build();
        }
        Integer documentId = songBlockService.documentIdForBlock(id);
        songVersionService.autoSaveVersion(documentId);
        return created(assembler.toModel(created, documentId));
    }

    /** Tints a line's background; a blank or unknown colour clears it. */
    @RequestMapping(value = "/{id}/highlight", method = RequestMethod.POST,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> setHighlight(
            @PathVariable Integer id,
            @RequestBody(required = false) SetSongBlockHighlightRequest request,
            Principal principal) {
        if (songBlockService.read(id) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        SongBlock block = songBlockService.setHighlight(id, request != null ? request.highlight() : null);
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.ok(assembler.toModel(block, songBlockService.documentIdForBlock(id)));
    }

    /** Reorders a line to an absolute {@code position} within the song. */
    @RequestMapping(value = "/{id}/move", method = RequestMethod.POST,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> move(
            @PathVariable Integer id,
            @RequestBody MoveSongBlockRequest request,
            Principal principal) {
        if (request == null || request.position() == null) {
            return new ResponseEntity<>(Map.of("position", "You must supply a value for Position."),
                    HttpStatus.BAD_REQUEST);
        }
        SongBlock current = songBlockService.read(id);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer documentId = songBlockService.documentIdForBlock(id);
        if (current.getOrder() != null && current.getOrder().equals(request.position())) {
            return ResponseEntity.ok(assembler.toModel(current, documentId));
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        SongBlock block = songBlockService.moveTo(id, request.position());
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.ok(assembler.toModel(block, documentId));
    }

    // --- undo / redo ------------------------------------------------------
    //
    // The stacks are session-scoped snapshots per song document, so a client
    // that authenticates per-request (no session) will find nothing to undo.
    // Same contract as the web editor's Edit menu.

    @RequestMapping(value = "/undo", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> undo(@RequestParam Integer documentId, Principal principal) {
        if (songBlockService.projectIdForDocument(documentId) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.undo(documentId);
        return list(documentId, principal);
    }

    @RequestMapping(value = "/redo", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> redo(@RequestParam Integer documentId, Principal principal) {
        if (songBlockService.projectIdForDocument(documentId) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.redo(documentId);
        return list(documentId, principal);
    }

    @RequestMapping(value = "/undoRedoStatus", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> undoRedoStatus(@RequestParam Integer documentId, Principal principal) {
        if (songBlockService.projectIdForDocument(documentId) == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("canUndo", songUndoRedoService.canUndo(documentId));
        status.put("canRedo", songUndoRedoService.canRedo(documentId));
        return ResponseEntity.ok(status);
    }

    private ResponseEntity<EntityModel<SongBlockResource>> created(EntityModel<SongBlockResource> resource) {
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }
}
