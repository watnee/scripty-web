package com.scripty.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WebModelAdvice {

    @Value("${app.asset-version:93}")
    private String assetVersion;

    @Value("${app.service-worker-enabled:true}")
    private boolean serviceWorkerEnabled;

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersion;
    }

    @ModelAttribute("serviceWorkerEnabled")
    public boolean serviceWorkerEnabled() {
        return serviceWorkerEnabled;
    }
}
