package com.scripty.controller;

import com.scripty.commandmodel.account.ChangePasswordCommandModel;
import com.scripty.service.UserService;
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

    @RequestMapping(value = "/password", method = RequestMethod.GET)
    public String changePasswordForm(Model model) {
        if (!model.containsAttribute("commandModel")) {
            model.addAttribute("commandModel", new ChangePasswordCommandModel());
        }
        return "account/change-password";
    }

    @RequestMapping(value = "/password", method = RequestMethod.POST)
    public String changePassword(@Valid @ModelAttribute("commandModel") ChangePasswordCommandModel commandModel,
                                 BindingResult bindingResult,
                                 Principal principal,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        if (!Objects.equals(commandModel.getNewPassword(), commandModel.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch",
                    "New password and confirmation do not match.");
        }

        if (bindingResult.hasErrors()) {
            return "account/change-password";
        }

        try {
            userService.changePassword(
                    principal.getName(),
                    commandModel.getCurrentPassword(),
                    commandModel.getNewPassword());
        } catch (IllegalArgumentException e) {
            model.addAttribute("passwordError", e.getMessage());
            return "account/change-password";
        }

        redirectAttributes.addFlashAttribute("passwordChanged", true);
        return "redirect:/account/password";
    }
}
