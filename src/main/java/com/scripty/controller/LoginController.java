package com.scripty.controller;

import com.scripty.config.PasskeySettings;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    private final PasskeySettings passkeySettings;

    public LoginController(PasskeySettings passkeySettings) {
        this.passkeySettings = passkeySettings;
    }

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/";
        }
        model.addAttribute("passkeysEnabled", passkeySettings.isEnabled());
        return "login";
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
