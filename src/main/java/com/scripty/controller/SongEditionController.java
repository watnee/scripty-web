package com.scripty.controller;

import com.scripty.dto.SongEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Named, switchable song versions, mirroring {@link ScriptEditionController}.
 * Any project member may switch versions; creating, renaming, deleting, and
 * publishing require script write access — the same rule the song editor uses.
 */
@Controller
@RequestMapping(value = "/song/edition")
public class SongEditionController {

    @Autowired
    SongEditionService songEditionService;

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    ProjectAccessSupport projectAccess;

    private boolean canAccessDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canEditDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    private String editorRedirect(Integer documentId, Integer editionId) {
        if (editionId != null) {
            return "redirect:/project/documents/edit?id=" + documentId + "&editionId=" + editionId;
        }
        return "redirect:/project/documents/edit?id=" + documentId;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer documentId,
                         @RequestParam(defaultValue = "") String name,
                         @RequestParam(required = false) Integer copyFromEditionId,
                         Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        SongEdition edition = songEditionService.createEdition(documentId, name, copyFromEditionId);
        if (edition == null) {
            return editorRedirect(documentId, null);
        }
        return editorRedirect(documentId, edition.getId());
    }

    @RequestMapping(value = "/switch")
    public String switchEdition(@RequestParam Integer documentId,
                                @RequestParam Integer editionId,
                                Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        boolean canBrowse = canEditDocument(documentId, principal);
        SongEdition edition = songEditionService.resolveForAccess(documentId, editionId, canBrowse);
        if (edition == null) {
            return editorRedirect(documentId, null);
        }
        return editorRedirect(documentId, edition.getId());
    }

    @RequestMapping(value = "/rename", method = RequestMethod.POST)
    public String rename(@RequestParam Integer documentId,
                         @RequestParam Integer editionId,
                         @RequestParam String name,
                         Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        songEditionService.renameEdition(editionId, documentId, name);
        return editorRedirect(documentId, editionId);
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer documentId,
                         @RequestParam Integer editionId,
                         Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        songEditionService.deleteEdition(editionId, documentId);
        SongEdition fallback = songEditionService.getDefaultForDocument(documentId);
        return editorRedirect(documentId, fallback != null ? fallback.getId() : null);
    }

    @RequestMapping(value = "/setDefault", method = RequestMethod.POST)
    public String setDefault(@RequestParam Integer documentId,
                             @RequestParam Integer editionId,
                             Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        songEditionService.setDefaultEdition(editionId, documentId);
        return editorRedirect(documentId, editionId);
    }

    @RequestMapping(value = "/setPublished", method = RequestMethod.POST)
    public String setPublished(@RequestParam Integer documentId,
                               @RequestParam Integer editionId,
                               Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        songEditionService.setPublishedEdition(editionId, documentId);
        return editorRedirect(documentId, editionId);
    }
}
