package com.scripty.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasskeySettingsTest {

    @Test
    void derivesRpIdAndOriginFromBaseUrl() {
        PasskeySettings settings = new PasskeySettings(true, "https://scripty.example.com");
        assertTrue(settings.isEnabled());
        assertEquals("scripty.example.com", settings.getRpId());
        assertEquals("https://scripty.example.com", settings.getOrigin());
    }

    @Test
    void keepsExplicitPortInOrigin() {
        PasskeySettings settings = new PasskeySettings(true, "http://localhost:8080");
        assertTrue(settings.isEnabled());
        assertEquals("localhost", settings.getRpId());
        assertEquals("http://localhost:8080", settings.getOrigin());
    }

    @Test
    void disabledWithoutBaseUrl() {
        assertFalse(new PasskeySettings(true, "").isEnabled());
        assertFalse(new PasskeySettings(true, null).isEnabled());
        assertFalse(new PasskeySettings(true, "not a url").isEnabled());
    }

    @Test
    void disabledWhenFlagIsOff() {
        assertFalse(new PasskeySettings(false, "https://scripty.example.com").isEnabled());
    }
}
