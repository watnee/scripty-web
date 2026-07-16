package com.scripty.controller;

import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongVersionService;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Snapshot history for a song, mirroring {@link ProjectVersionController}.
 * Any project member may view the history; saving, restoring and deleting
 * require script write access, matching both the screenplay and the song
 * editor, which renders read-only without it.
 */
@Controller
@RequestMapping(value = "/song/version")
public class SongVersionController {

    @Autowired
    SongVersionService songVersionService;

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

    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer documentId, Model model, Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        SongVersionHistoryViewModel viewModel = songVersionService.getVersionHistoryViewModel(documentId);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("canEditScript", canEditDocument(documentId, principal));
        return "song/versionHistory";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer documentId,
                         @RequestParam(defaultValue = "") String label,
                         Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        if (label == null || label.isBlank()) {
            label = "Version";
        }
        songVersionService.createVersion(documentId, label);
        return "redirect:/song/version/list?documentId=" + documentId;
    }

    @RequestMapping(value = "/restore", method = RequestMethod.POST)
    public String restore(@RequestParam Integer id,
                          @RequestParam Integer documentId,
                          Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        if (!songVersionService.restoreVersionForDocument(id, documentId)) {
            return "redirect:/song/version/list?documentId=" + documentId;
        }
        return "redirect:/project/documents/edit?id=" + documentId;
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id,
                         @RequestParam Integer documentId,
                         Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        songVersionService.deleteVersionForDocument(id, documentId);
        return "redirect:/song/version/list?documentId=" + documentId;
    }
}
