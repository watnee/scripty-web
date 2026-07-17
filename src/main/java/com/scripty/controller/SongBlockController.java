package com.scripty.controller;

import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * HTMX endpoints backing the song block editor. Structural changes return the
 * refreshed block list fragment; content edits persist silently (204). Every
 * operation is scoped to the active song version ({@link SongEdition}); block-id
 * operations resolve the version from the block, while document-keyed ones take
 * the editor's active {@code editionId} (defaulting to the song's default
 * version when absent).
 */
@Controller
@RequestMapping(value = "/song/block")
public class SongBlockController {

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

    // Song editing follows the same rule as the screenplay: writer (or admin)
    // permission on a project the user can access. ProjectAccessSupport's own
    // canEditBlock resolves screenplay blocks, so song block ids are mapped to a
    // project here before the check.
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

    /** Resolves a missing editionId to the song's default version. */
    private Integer effectiveEdition(Integer documentId, Integer editionId) {
        SongEdition edition = songEditionService.requireForDocument(documentId, editionId);
        if (edition == null) {
            edition = songEditionService.ensureDefaultEdition(documentId);
        }
        return edition != null ? edition.getId() : editionId;
    }

    private String renderList(Integer documentId, Integer editionId, Integer focusBlockId, Model model) {
        model.addAttribute("blocks", songBlockService.getBlocks(documentId, editionId));
        model.addAttribute("focusBlockId", focusBlockId);
        model.addAttribute("editionId", editionId);
        return "songblock/blocks :: blocks";
    }

    @RequestMapping(value = "/edit", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Void> edit(@RequestParam Integer id,
                                     @RequestParam(defaultValue = "") String content,
                                     Principal principal) {
        if (!canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.editContent(id, content);
        songVersionService.autoSaveVersionForBlock(id);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/createBelow", method = RequestMethod.POST)
    public String createBelow(@RequestParam Integer id,
                              @RequestParam(required = false) String content,
                              Model model,
                              Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        SongBlock created = songBlockService.createBelow(id, content);
        Integer documentId = songBlockService.documentIdForBlock(id);
        Integer editionId = songBlockService.editionIdForBlock(id);
        songVersionService.autoSaveVersion(documentId, editionId);
        return renderList(documentId, editionId, created != null ? created.getId() : null, model);
    }

    @RequestMapping(value = "/append", method = RequestMethod.POST)
    public String append(@RequestParam Integer documentId,
                         @RequestParam(required = false) Integer editionId,
                         Model model,
                         Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "songblock/blocks :: forbidden";
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        songUndoRedoService.recordCheckpoint(documentId, resolved);
        SongBlock created = songBlockService.appendBlock(documentId, resolved);
        songVersionService.autoSaveVersion(documentId, resolved);
        return renderList(documentId, resolved, created != null ? created.getId() : null, model);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id, Model model, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        // Resolve the version before the block is gone.
        Integer editionId = songBlockService.editionIdForBlock(id);
        songUndoRedoService.recordCheckpointForBlock(id);
        Integer documentId = songBlockService.deleteBlock(id);
        songVersionService.autoSaveVersion(documentId, editionId);
        return renderList(documentId, editionId, null, model);
    }

    @RequestMapping(value = "/setHighlight", method = RequestMethod.POST)
    public String setHighlight(@RequestParam Integer id,
                               @RequestParam(required = false) String highlight,
                               Model model,
                               Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.setHighlight(id, highlight);
        songVersionService.autoSaveVersionForBlock(id);
        return renderList(songBlockService.documentIdForBlock(id), songBlockService.editionIdForBlock(id), id, model);
    }

    @RequestMapping(value = "/moveUp", method = RequestMethod.POST)
    public String moveUp(@RequestParam Integer id, Model model, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.moveUp(id);
        songVersionService.autoSaveVersionForBlock(id);
        return renderList(songBlockService.documentIdForBlock(id), songBlockService.editionIdForBlock(id), id, model);
    }

    @RequestMapping(value = "/moveDown", method = RequestMethod.POST)
    public String moveDown(@RequestParam Integer id, Model model, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.moveDown(id);
        songVersionService.autoSaveVersionForBlock(id);
        return renderList(songBlockService.documentIdForBlock(id), songBlockService.editionIdForBlock(id), id, model);
    }

    @RequestMapping(value = "/moveTo", method = RequestMethod.POST)
    public String moveTo(@RequestParam Integer id,
                         @RequestParam Integer position,
                         Model model,
                         Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.moveTo(id, position != null ? position : 0);
        songVersionService.autoSaveVersionForBlock(id);
        return renderList(songBlockService.documentIdForBlock(id), songBlockService.editionIdForBlock(id), id, model);
    }

    // --- undo / redo ------------------------------------------------------
    //
    // Mirrors the screenplay's Edit menu (/project/undo, /project/redo,
    // /project/undoRedoStatus): the stacks are session-scoped snapshots, so they
    // last for the session and are per song version.

    @RequestMapping(value = "/undo", method = RequestMethod.POST)
    public String undo(@RequestParam Integer documentId,
                       @RequestParam(required = false) Integer editionId,
                       Model model,
                       Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "songblock/blocks :: forbidden";
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        songUndoRedoService.undo(documentId, resolved);
        return renderList(documentId, resolved, null, model);
    }

    @RequestMapping(value = "/redo", method = RequestMethod.POST)
    public String redo(@RequestParam Integer documentId,
                       @RequestParam(required = false) Integer editionId,
                       Model model,
                       Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "songblock/blocks :: forbidden";
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        songUndoRedoService.redo(documentId, resolved);
        return renderList(documentId, resolved, null, model);
    }

    @RequestMapping(value = "/undoRedoStatus", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> undoRedoStatus(@RequestParam Integer documentId,
                                                              @RequestParam(required = false) Integer editionId,
                                                              Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        Map<String, Object> status = new HashMap<>();
        status.put("canUndo", songUndoRedoService.canUndo(documentId, resolved));
        status.put("canRedo", songUndoRedoService.canRedo(documentId, resolved));
        return ResponseEntity.ok(status);
    }
}
