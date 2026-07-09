package com.scripty.controller;

import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectVersionService;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/project/version")
public class ProjectVersionController {

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer projectId,
                       @RequestParam(required = false) Integer editionId,
                       Model model,
                       Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return "redirect:/project/list";
        }
        VersionHistoryViewModel viewModel = projectVersionService.getVersionHistoryViewModel(projectId, editionId);
        model.addAttribute("viewModel", viewModel);
        model.addAttribute("canEditScript", projectAccess.canEditScript(projectId, principal));
        return "project/versionHistory";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer projectId,
                         @RequestParam(required = false) Integer editionId,
                         @RequestParam(defaultValue = "") String label,
                         Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        if (label == null || label.isBlank()) {
            label = "Version";
        }
        projectVersionService.createVersion(projectId, editionId, label);
        String redirect = "redirect:/project/version/list?projectId=" + projectId;
        if (editionId != null) {
            redirect += "&editionId=" + editionId;
        }
        return redirect;
    }

    @RequestMapping(value = "/restore", method = RequestMethod.POST)
    public String restore(@RequestParam Integer id, @RequestParam Integer projectId,
                          @RequestParam(required = false) Integer editionId,
                          Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        if (!projectVersionService.restoreVersionForProject(id, projectId)) {
            return "redirect:/project/list";
        }
        String redirect = "redirect:/project/show?id=" + projectId;
        if (editionId != null) {
            redirect += "&editionId=" + editionId;
        }
        return redirect;
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id, @RequestParam Integer projectId,
                         @RequestParam(required = false) Integer editionId,
                         Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        if (!projectVersionService.deleteVersionForProject(id, projectId)) {
            return "redirect:/project/list";
        }
        String redirect = "redirect:/project/version/list?projectId=" + projectId;
        if (editionId != null) {
            redirect += "&editionId=" + editionId;
        }
        return redirect;
    }
}
