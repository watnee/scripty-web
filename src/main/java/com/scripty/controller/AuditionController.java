package com.scripty.controller;

import com.scripty.service.AuditionService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/audition")
public class AuditionController {

    @Autowired
    AuditionService auditionService;

    @RequestMapping(value = "/set", method = RequestMethod.GET)
    public String setAuditionsGet(@RequestParam(required = false) Integer projectId) {
        if (projectId != null) {
            return "redirect:/actor/list?projectId=" + projectId;
        }
        return "redirect:/actor/list";
    }

    @RequestMapping(value = "/set", method = RequestMethod.POST)
    public String setAuditions(@RequestParam Integer actorId,
                               @RequestParam Integer projectId,
                               @RequestParam(required = false) List<Integer> characterIds) {

        auditionService.setAuditionsForActorInProject(actorId, projectId, characterIds);

        return "redirect:/actor/list?projectId=" + projectId;
    }
}
