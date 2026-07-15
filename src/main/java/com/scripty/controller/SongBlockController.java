package com.scripty.controller;

import com.scripty.dto.Block;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.SongBlockService;
import com.scripty.service.TextDocumentService;
import com.scripty.service.UserService;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.security.Principal;
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
 * HTMX endpoints for the song (text-document) block editor. Structural changes (add / delete /
 * move) re-render the whole block list for consistency; inline content edits swap just the
 * edited cell to preserve the caret. Every mutation re-syncs any screenplay copies already
 * inserted from the song and auto-saves a project version, mirroring the textarea save flow in
 * {@link TextDocumentController#save}.
 */
@Controller
@RequestMapping(value = "/project/documents/block")
public class SongBlockController {

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    UserService userService;

    // --- Inline content edit (partial swaps) ---

    @RequestMapping(value = "/editInline")
    public String editInline(@RequestParam Integer id, Model model, Principal principal) {
        if (denyEditBlock(id, principal)) {
            return deny();
        }
        Block block = songBlockService.read(id);
        if (block == null) {
            return deny();
        }
        model.addAttribute("block", block);
        return "block/songEditInline";
    }

    @RequestMapping(value = "/editInline", method = RequestMethod.POST)
    public String saveEditInline(@RequestParam Integer id,
                                 @RequestParam(defaultValue = "") String content,
                                 Model model,
                                 Principal principal) {
        if (denyEditBlock(id, principal)) {
            return deny();
        }
        Block block = songBlockService.editContent(id, content);
        if (block == null) {
            return deny();
        }
        syncAndVersion(block, principal);
        model.addAttribute("block", block);
        return "block/songShowInline";
    }

    @RequestMapping(value = "/showInline")
    public String showInline(@RequestParam Integer id, Model model, Principal principal) {
        if (denyAccessBlock(id, principal)) {
            return deny();
        }
        Block block = songBlockService.read(id);
        if (block == null) {
            return deny();
        }
        model.addAttribute("block", block);
        return "block/songShowInline";
    }

    // --- Structural changes (re-render the whole list) ---

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer documentId, Model model, Principal principal) {
        TextDocumentViewModel doc = accessibleDoc(documentId, principal);
        if (doc == null) {
            return deny();
        }
        Block created = songBlockService.createAtEnd(documentId);
        if (created != null) {
            syncAndVersion(created, principal);
        }
        return renderList(documentId, doc.getProjectId(), created != null ? created.getId() : null, model);
    }

    @RequestMapping(value = "/createBelow", method = RequestMethod.POST)
    public String createBelow(@RequestParam Integer id, Model model, Principal principal) {
        if (denyEditBlock(id, principal)) {
            return deny();
        }
        Block created = songBlockService.createBelow(id, "");
        if (created == null) {
            return deny();
        }
        syncAndVersion(created, principal);
        return renderList(documentId(created), projectId(created), created.getId(), model);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id, Model model, Principal principal) {
        if (denyEditBlock(id, principal)) {
            return deny();
        }
        Block block = songBlockService.delete(id);
        if (block == null) {
            return deny();
        }
        syncAndVersion(block, principal);
        return renderList(documentId(block), projectId(block), null, model);
    }

    @RequestMapping(value = "/moveUp", method = RequestMethod.POST)
    public String moveUp(@RequestParam Integer id, Model model, Principal principal) {
        if (denyEditBlock(id, principal)) {
            return deny();
        }
        Block block = songBlockService.moveUp(id);
        if (block == null) {
            return deny();
        }
        syncAndVersion(block, principal);
        return renderList(documentId(block), projectId(block), null, model);
    }

    @RequestMapping(value = "/moveDown", method = RequestMethod.POST)
    public String moveDown(@RequestParam Integer id, Model model, Principal principal) {
        if (denyEditBlock(id, principal)) {
            return deny();
        }
        Block block = songBlockService.moveDown(id);
        if (block == null) {
            return deny();
        }
        syncAndVersion(block, principal);
        return renderList(documentId(block), projectId(block), null, model);
    }

    /** Drag-and-drop reorder. The client already reordered the DOM, so return a bare 200. */
    @RequestMapping(value = "/moveTo", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> moveTo(@RequestParam Integer id,
                                         @RequestParam int position,
                                         Principal principal) {
        if (denyEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }
        Block block = songBlockService.moveTo(id, position);
        if (block == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("");
        }
        syncAndVersion(block, principal);
        return ResponseEntity.ok("");
    }

    // --- Helpers ---

    private String renderList(Integer documentId, Integer projectId, Integer focusBlockId, Model model) {
        model.addAttribute("blocks", songBlockService.listBlocks(documentId));
        model.addAttribute("documentId", documentId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("focusBlockId", focusBlockId);
        return "block/songBlockList :: list";
    }

    /** Re-sync inserted screenplay copies and snapshot a version, mirroring the textarea save flow. */
    private void syncAndVersion(Block block, Principal principal) {
        Integer docId = documentId(block);
        Integer projectId = block.getProject() != null ? block.getProject().getId() : null;
        if (docId == null || projectId == null) {
            return;
        }
        User user = currentUser(principal);
        if (textDocumentService.syncInsertedBlocks(docId, user)) {
            projectVersionService.autoSaveVersion(projectId);
        }
    }

    private Integer documentId(Block block) {
        return block != null && block.getTextDocument() != null ? block.getTextDocument().getId() : null;
    }

    private Integer projectId(Block block) {
        return block != null && block.getProject() != null ? block.getProject().getId() : null;
    }

    private TextDocumentViewModel accessibleDoc(Integer documentId, Principal principal) {
        User user = currentUser(principal);
        TextDocumentViewModel doc = textDocumentService.getViewModel(documentId, user);
        if (doc == null || !projectAccess.canEditScript(doc.getProjectId(), user)) {
            return null;
        }
        return doc;
    }

    private boolean denyEditBlock(Integer blockId, Principal principal) {
        return !projectAccess.canEditBlock(blockId, principal);
    }

    private boolean denyAccessBlock(Integer blockId, Principal principal) {
        return !projectAccess.canAccessBlock(blockId, principal);
    }

    private String deny() {
        return "redirect:/project/list";
    }

    private User currentUser(Principal principal) {
        return principal == null ? null : userService.readByUsername(principal.getName());
    }
}
