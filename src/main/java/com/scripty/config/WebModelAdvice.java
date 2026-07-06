package com.scripty.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WebModelAdvice {

    @Value("${app.asset-version:55}")
    private String assetVersion;

    @ModelAttribute("assetVersion")
    public String assetVersion() {
        return assetVersion;
    }
}
