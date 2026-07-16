package com.scripty.controller;

import com.scripty.dto.SongBlock;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
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
 * refreshed block list fragment; content edits persist silently (204).
 */
@Controller
@RequestMapping(value = "/song/block")
public class SongBlockController {

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    SongUndoRedoService songUndoRedoService;

    @Autowired
    SongVersionService songVersionService;

    // Song editing follows the same rule as the song editor itself: any project
    // member may edit (project access), unlike screenplay blocks which need write.
    private boolean canEditBlock(Integer blockId, Principal principal) {
        Integer projectId = songBlockService.projectIdForBlock(blockId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canEditDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private String renderList(Integer documentId, Integer focusBlockId, Model model) {
        model.addAttribute("blocks", songBlockService.getBlocks(documentId));
        model.addAttribute("focusBlockId", focusBlockId);
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
        songVersionService.autoSaveVersion(documentId);
        return renderList(documentId, created != null ? created.getId() : null, model);
    }

    @RequestMapping(value = "/append", method = RequestMethod.POST)
    public String append(@RequestParam Integer documentId, Model model, Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpoint(documentId);
        SongBlock created = songBlockService.appendBlock(documentId);
        songVersionService.autoSaveVersion(documentId);
        return renderList(documentId, created != null ? created.getId() : null, model);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id, Model model, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        Integer documentId = songBlockService.deleteBlock(id);
        songVersionService.autoSaveVersion(documentId);
        return renderList(documentId, null, model);
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
        return renderList(songBlockService.documentIdForBlock(id), id, model);
    }

    @RequestMapping(value = "/moveUp", method = RequestMethod.POST)
    public String moveUp(@RequestParam Integer id, Model model, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.moveUp(id);
        songVersionService.autoSaveVersionForBlock(id);
        return renderList(songBlockService.documentIdForBlock(id), id, model);
    }

    @RequestMapping(value = "/moveDown", method = RequestMethod.POST)
    public String moveDown(@RequestParam Integer id, Model model, Principal principal) {
        if (!canEditBlock(id, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.recordCheckpointForBlock(id);
        songBlockService.moveDown(id);
        songVersionService.autoSaveVersionForBlock(id);
        return renderList(songBlockService.documentIdForBlock(id), id, model);
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
        return renderList(songBlockService.documentIdForBlock(id), id, model);
    }

    // --- undo / redo ------------------------------------------------------
    //
    // Mirrors the screenplay's Edit menu (/project/undo, /project/redo,
    // /project/undoRedoStatus): the stacks are session-scoped snapshots, so they
    // last for the session and are per song document.

    @RequestMapping(value = "/undo", method = RequestMethod.POST)
    public String undo(@RequestParam Integer documentId, Model model, Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.undo(documentId);
        return renderList(documentId, null, model);
    }

    @RequestMapping(value = "/redo", method = RequestMethod.POST)
    public String redo(@RequestParam Integer documentId, Model model, Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "songblock/blocks :: forbidden";
        }
        songUndoRedoService.redo(documentId);
        return renderList(documentId, null, model);
    }

    @RequestMapping(value = "/undoRedoStatus", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> undoRedoStatus(@RequestParam Integer documentId,
                                                              Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Map<String, Object> status = new HashMap<>();
        status.put("canUndo", songUndoRedoService.canUndo(documentId));
        status.put("canRedo", songUndoRedoService.canRedo(documentId));
        return ResponseEntity.ok(status);
    }
}
