package com.scripty.controller;

import com.scripty.service.ProjectVersionService;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
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

    @RequestMapping(value = "/list")
    public String list(@RequestParam Integer projectId, Model model) {
        VersionHistoryViewModel viewModel = projectVersionService.getVersionHistoryViewModel(projectId);
        model.addAttribute("viewModel", viewModel);
        return "project/versionHistory";
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer projectId,
                         @RequestParam(defaultValue = "") String label) {
        if (label == null || label.isBlank()) {
            label = "Version";
        }
        projectVersionService.createVersion(projectId, label);
        return "redirect:/project/version/list?projectId=" + projectId;
    }

    @RequestMapping(value = "/restore", method = RequestMethod.POST)
    public String restore(@RequestParam Integer id, @RequestParam Integer projectId) {
        projectVersionService.restoreVersion(id);
        return "redirect:/project/show?id=" + projectId;
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer id, @RequestParam Integer projectId) {
        projectVersionService.deleteVersion(id);
        return "redirect:/project/version/list?projectId=" + projectId;
    }
}
