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
 * Access follows the song editor's rule: any project member may view and
 * restore, unlike the screenplay which requires script write access.
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

    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer documentId, Model model, Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        SongVersionHistoryViewModel viewModel = songVersionService.getVersionHistoryViewModel(documentId);
        model.addAttribute("viewModel", viewModel);
        return "song/versionHistory";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer documentId,
                         @RequestParam(defaultValue = "") String label,
                         Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
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
        if (!canAccessDocument(documentId, principal)) {
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
        if (!canAccessDocument(documentId, principal)) {
            return "redirect:/project/list";
        }
        songVersionService.deleteVersionForDocument(id, documentId);
        return "redirect:/song/version/list?documentId=" + documentId;
    }
}
