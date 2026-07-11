package com.scripty.controller;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.service.PasswordRecoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/forgot-password")
public class ForgotPasswordController {

    private final PasswordRecoveryService recoveryService;

    @Autowired
    public ForgotPasswordController(PasswordRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @GetMapping
    public String requestForm() {
        return "forgot-password/request";
    }

    @PostMapping
    public String processRequest(@RequestParam("email") String email, Model model) {
        try {
            recoveryService.sendRecoveryEmail(email);
        } catch (Exception e) {
            // Fail gracefully
        }
        // Always display success to prevent email enumeration
        model.addAttribute("successMessage", 
                "If that address is registered, instructions to reset your password have been sent.");
        return "forgot-password/request";
    }

    @GetMapping("/reset")
    public String resetForm(@RequestParam("token") String token, Model model) {
        try {
            PasswordRecoveryToken recoveryToken = recoveryService.validateToken(token);
            model.addAttribute("valid", true);
            model.addAttribute("token", token);
            model.addAttribute("username", recoveryToken.getUser().getUsername());
        } catch (IllegalArgumentException e) {
            model.addAttribute("valid", false);
            model.addAttribute("errorMessage", e.getMessage());
        }
        return "forgot-password/reset";
    }

    @PostMapping("/reset")
    public String processReset(@RequestParam("token") String token,
                               @RequestParam("password") String password,
                               @RequestParam("confirmPassword") String confirmPassword,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        // Validate matching passwords first
        if (password == null || password.isEmpty()) {
            model.addAttribute("valid", true);
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", "Password is required.");
            return "forgot-password/reset";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("valid", true);
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "forgot-password/reset";
        }

        try {
            recoveryService.resetPassword(token, password);
            redirectAttributes.addFlashAttribute("passwordResetSuccess", 
                    "Your password has been successfully reset. Please sign in with your new password.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("valid", true);
            model.addAttribute("token", token);
            model.addAttribute("errorMessage", e.getMessage());
            return "forgot-password/reset";
        }
    }
}
