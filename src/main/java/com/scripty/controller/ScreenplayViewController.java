package com.scripty.controller;

import com.scripty.service.ViewInvitationService;
import com.scripty.viewmodel.invitation.ScreenplayViewViewModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Public read-only screenplay page opened from view-invitation emails.
 * No login required: access is granted by the emailed token alone.
 */
@Controller
public class ScreenplayViewController {

    @Autowired
    ViewInvitationService viewInvitationService;

    @RequestMapping(value = "/view", method = RequestMethod.GET)
    public String view(@RequestParam(required = false) String token, Model model) {
        ScreenplayViewViewModel viewModel = viewInvitationService.getScreenplayForToken(token);
        model.addAttribute("viewModel", viewModel);
        return "invitation/view";
    }
}
