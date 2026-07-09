package com.scripty.controller;

import com.scripty.dto.ScriptEdition;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ScriptEditionService;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/project/edition")
public class ScriptEditionController {

    @Autowired
    ScriptEditionService scriptEditionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public String create(@RequestParam Integer projectId,
                         @RequestParam(defaultValue = "") String name,
                         @RequestParam(required = false) Integer copyFromEditionId,
                         Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        ScriptEdition edition = scriptEditionService.createEdition(projectId, name, copyFromEditionId);
        if (edition == null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/project/show?id=" + projectId + "&editionId=" + edition.getId();
    }

    @RequestMapping(value = "/switch")
    public String switchEdition(@RequestParam Integer projectId,
                                @RequestParam Integer editionId,
                                Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return "redirect:/project/list";
        }
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        if (edition == null) {
            return "redirect:/project/show?id=" + projectId;
        }
        return "redirect:/project/show?id=" + projectId + "&editionId=" + edition.getId();
    }

    @RequestMapping(value = "/rename", method = RequestMethod.POST)
    public String rename(@RequestParam Integer projectId,
                         @RequestParam Integer editionId,
                         @RequestParam String name,
                         Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        scriptEditionService.renameEdition(editionId, projectId, name);
        return "redirect:/project/show?id=" + projectId + "&editionId=" + editionId;
    }

    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public String delete(@RequestParam Integer projectId,
                         @RequestParam Integer editionId,
                         Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        scriptEditionService.deleteEdition(editionId, projectId);
        ScriptEdition fallback = scriptEditionService.getDefaultForProject(projectId);
        if (fallback != null) {
            return "redirect:/project/show?id=" + projectId + "&editionId=" + fallback.getId();
        }
        return "redirect:/project/show?id=" + projectId;
    }

    @RequestMapping(value = "/setDefault", method = RequestMethod.POST)
    public String setDefault(@RequestParam Integer projectId,
                             @RequestParam Integer editionId,
                             Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return "redirect:/project/list";
        }
        scriptEditionService.setDefaultEdition(editionId, projectId);
        return "redirect:/project/show?id=" + projectId + "&editionId=" + editionId;
    }
}
