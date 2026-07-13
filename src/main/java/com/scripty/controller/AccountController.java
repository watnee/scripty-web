package com.scripty.controller;

import com.scripty.commandmodel.account.ChangePasswordCommandModel;
import com.scripty.config.PasskeySettings;
import com.scripty.dto.User;
import com.scripty.security.ForcedPasswordChangeFilter;
import com.scripty.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping(value = "/account")
public class AccountController {

    @Autowired
    UserService userService;

    @Autowired
    PasskeySettings passkeySettings;

    @RequestMapping(value = "/password", method = RequestMethod.GET)
    public String changePasswordForm(Model model, HttpSession session, Principal principal) {
        if (!model.containsAttribute("commandModel")) {
            model.addAttribute("commandModel", new ChangePasswordCommandModel());
        }
        model.addAttribute("forcedChange", isForcedChange(session, principal));
        model.addAttribute("passkeysEnabled", passkeySettings.isEnabled());
        return "account/change-password";
    }

    @RequestMapping(value = "/password", method = RequestMethod.POST)
    public String changePassword(@Valid @ModelAttribute("commandModel") ChangePasswordCommandModel commandModel,
                                 BindingResult bindingResult,
                                 Principal principal,
                                 Model model,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        boolean forcedChange = isForcedChange(session, principal);

        if (!Objects.equals(commandModel.getNewPassword(), commandModel.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch",
                    "New password and confirmation do not match.");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("forcedChange", forcedChange);
            model.addAttribute("passkeysEnabled", passkeySettings.isEnabled());
            return "account/change-password";
        }

        try {
            userService.changePassword(
                    principal.getName(),
                    commandModel.getCurrentPassword(),
                    commandModel.getNewPassword());
        } catch (IllegalArgumentException e) {
            model.addAttribute("passwordError", e.getMessage());
            model.addAttribute("forcedChange", forcedChange);
            model.addAttribute("passkeysEnabled", passkeySettings.isEnabled());
            return "account/change-password";
        }

        session.setAttribute(ForcedPasswordChangeFilter.SESSION_ATTR, Boolean.FALSE);
        redirectAttributes.addFlashAttribute("passwordChanged", true);
        if (forcedChange) {
            // First-login (bootstrap credential) flow: drop the admin straight
            // into the workspace instead of leaving them on the account page.
            return "redirect:/project/list";
        }
        return "redirect:/account/password";
    }

    /**
     * The session attribute is only populated once ForcedPasswordChangeFilter has
     * intercepted a non-exempt request; a user who lands on the (exempt)
     * change-password page directly after login would miss it, so fall back to
     * the database flag and cache the answer the same way the filter does.
     */
    private boolean isForcedChange(HttpSession session, Principal principal) {
        Boolean cached = (Boolean) session.getAttribute(ForcedPasswordChangeFilter.SESSION_ATTR);
        if (cached != null) {
            return cached;
        }
        if (principal == null) {
            return false;
        }
        User user = userService.readByUsername(principal.getName());
        boolean required = user != null && user.isPasswordChangeRequired();
        session.setAttribute(ForcedPasswordChangeFilter.SESSION_ATTR, required);
        return required;
    }
}
