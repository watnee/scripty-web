package com.scripty.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WebModelAdvice {

    private final String assetVersion;
    private final boolean serviceWorkerEnabled;

    public WebModelAdvice(FeatureFlags featureFlags, @Value("${app.asset-version:228}") String assetVersion) {
        this.assetVersion = assetVersion;
        this.serviceWorkerEnabled = featureFlags.isEnabled(FeatureFlag.SERVICE_WORKER);
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
