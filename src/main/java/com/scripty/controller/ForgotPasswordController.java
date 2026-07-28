package com.scripty.controller;

import com.scripty.dto.PasswordRecoveryToken;
import com.scripty.security.PasswordResetRateLimiter;
import com.scripty.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordController.class);

    private final PasswordRecoveryService recoveryService;
    private final PasswordResetRateLimiter rateLimiter;

    @Autowired
    public ForgotPasswordController(PasswordRecoveryService recoveryService,
                                    PasswordResetRateLimiter rateLimiter) {
        this.recoveryService = recoveryService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public String requestForm() {
        return "forgot-password/request";
    }

    @PostMapping
    public String processRequest(@RequestParam("email") String email, Model model,
                                 HttpServletRequest request) {
        // A refusal is a silent one. Saying "too many attempts" would tell a
        // stranger they had found a real address, which is the whole thing this
        // page is careful not to say — so the page below is the same either way.
        if (!rateLimiter.tryAcquire(email, request.getRemoteAddr())) {
            log.warn("Password recovery rate limit reached; email not sent. ip={}",
                    request.getRemoteAddr());
        } else {
            try {
                recoveryService.sendRecoveryEmail(email);
            } catch (Exception e) {
                // The response stays generic to prevent email enumeration, but the
                // failure must be visible to operators.
                log.error("Password recovery email failed", e);
            }
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
            model.addAttribute("email", recoveryToken.getUser().getEmail());
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
