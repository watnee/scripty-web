package com.scripty.controller;

import com.scripty.commandmodel.invitation.AcceptInvitationCommandModel;
import com.scripty.commandmodel.invitation.SendInvitationCommandModel;
import com.scripty.commandmodel.invitation.SendViewInvitationCommandModel;
import com.scripty.dto.User;
import com.scripty.service.InvitationService;
import com.scripty.service.UserService;
import com.scripty.service.ViewInvitationService;
import com.scripty.viewmodel.invitation.AcceptInvitationViewModel;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value = "/invitation")
public class InvitationController {

    @Autowired
    InvitationService invitationService;

    @Autowired
    ViewInvitationService viewInvitationService;

    @Autowired
    UserService userService;

    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public String send(@Valid @ModelAttribute("inviteCommand") SendInvitationCommandModel command,
                       BindingResult bindingResult,
                       @RequestParam(defaultValue = "show") String returnTo,
                       RedirectAttributes redirectAttributes) {
        Integer projectId = command.getProjectId();
        String redirect = redirectForProject(projectId, returnTo);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("inviteError",
                    bindingResult.getFieldError() != null
                            ? bindingResult.getFieldError().getDefaultMessage()
                            : "Could not send invitation.");
            return redirect;
        }

        try {
            invitationService.sendInvitation(command, currentUser());
            // Generic success — do not reveal whether the email already had an account.
            redirectAttributes.addFlashAttribute("inviteSuccess",
                    "If that address is eligible, an invitation will be sent.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("inviteError", e.getMessage());
        }
        return redirect;
    }

    @RequestMapping(value = "/revoke", method = RequestMethod.POST)
    public String revoke(@RequestParam Integer id,
                         @RequestParam Integer projectId,
                         @RequestParam(defaultValue = "production") String returnTo,
                         RedirectAttributes redirectAttributes) {
        try {
            invitationService.revoke(id, projectId, currentUser());
            redirectAttributes.addFlashAttribute("inviteSuccess", "Invitation revoked.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("inviteError", e.getMessage());
        }
        return redirectForProject(projectId, returnTo);
    }

    @RequestMapping(value = "/view/send", method = RequestMethod.POST)
    public String sendViewInvite(@Valid @ModelAttribute("viewInviteCommand") SendViewInvitationCommandModel command,
                                 BindingResult bindingResult,
                                 @RequestParam(defaultValue = "show") String returnTo,
                                 RedirectAttributes redirectAttributes) {
        String redirect = redirectForProject(command.getProjectId(), returnTo);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("viewInviteError",
                    bindingResult.getFieldError() != null
                            ? bindingResult.getFieldError().getDefaultMessage()
                            : "Could not send invitation.");
            return redirect;
        }

        try {
            viewInvitationService.sendInvitation(command, currentUser());
            redirectAttributes.addFlashAttribute("viewInviteSuccess",
                    "A view-only link is on its way to " + command.getEmail().trim() + ".");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("viewInviteError", e.getMessage());
        }
        return redirect;
    }

    @RequestMapping(value = "/view/revoke", method = RequestMethod.POST)
    public String revokeViewInvite(@RequestParam Integer id,
                                   @RequestParam Integer projectId,
                                   @RequestParam(defaultValue = "show") String returnTo,
                                   RedirectAttributes redirectAttributes) {
        try {
            viewInvitationService.revoke(id, projectId, currentUser());
            redirectAttributes.addFlashAttribute("viewInviteSuccess", "View access revoked.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("viewInviteError", e.getMessage());
        }
        return redirectForProject(projectId, returnTo);
    }

    @RequestMapping(value = "/accept", method = RequestMethod.GET)
    public String acceptForm(@RequestParam String token, Model model) {
        AcceptInvitationViewModel viewModel = invitationService.getAcceptViewModel(token);
        model.addAttribute("viewModel", viewModel);
        AcceptInvitationCommandModel commandModel = new AcceptInvitationCommandModel();
        commandModel.setToken(token);
        model.addAttribute("commandModel", commandModel);
        return "invitation/accept";
    }

    @RequestMapping(value = "/accept", method = RequestMethod.POST)
    public String accept(@Valid @ModelAttribute("commandModel") AcceptInvitationCommandModel commandModel,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        AcceptInvitationViewModel viewModel = invitationService.getAcceptViewModel(commandModel.getToken());
        if (!viewModel.isValid()) {
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            return "invitation/accept";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            return "invitation/accept";
        }

        try {
            invitationService.acceptInvitation(commandModel);
        } catch (IllegalArgumentException e) {
            model.addAttribute("viewModel", viewModel);
            model.addAttribute("commandModel", commandModel);
            model.addAttribute("acceptError", e.getMessage());
            return "invitation/accept";
        }

        redirectAttributes.addFlashAttribute("inviteAccepted", true);
        return "redirect:/login";
    }

    private String redirectForProject(Integer projectId, String returnTo) {
        if (projectId == null) {
            return "redirect:/project/list";
        }
        if ("production".equals(returnTo)) {
            return "redirect:/project/production?id=" + projectId;
        }
        return "redirect:/project/show?id=" + projectId;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.readByUsername(authentication.getName());
    }
}
