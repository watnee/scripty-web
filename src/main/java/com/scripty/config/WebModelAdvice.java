package com.scripty.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.scripty.service.CapitalizationPreferences;
import com.scripty.service.UserService;

@ControllerAdvice
public class WebModelAdvice {

    private final String assetVersion;
    private final boolean serviceWorkerEnabled;
    private final UserService userService;

    public WebModelAdvice(FeatureFlags featureFlags,
                          UserService userService,
                          @Value("${app.asset-version:249}") String assetVersion) {
        this.assetVersion = assetVersion;
        this.userService = userService;
        this.serviceWorkerEnabled = featureFlags.isEnabled(FeatureFlag.SERVICE_WORKER);
    }

    /**
     * Rendered onto {@code <html>} so the first paint already matches the user's
     * preference — waiting for auto-caps-toggle.js would flash uppercase first.
     */
    @ModelAttribute("autoCaps")
    public CapitalizationPreferences autoCaps() {
        String username = currentUserId();
        return username == null
                ? CapitalizationPreferences.ALL
                : userService.readCapitalizationPreferences(username);
    }

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersion;
    }

    @ModelAttribute("serviceWorkerEnabled")
    public boolean serviceWorkerEnabled() {
        return serviceWorkerEnabled;
    }

    @ModelAttribute("currentUserId")
    public String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }
}
