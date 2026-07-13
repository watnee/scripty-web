package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.scripty.config.PasskeySettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

class LoginControllerTest {

    private final LoginController controller = new LoginController(
            new PasskeySettings(true, "http://localhost:8080"));

    @Test
    void showsLoginWhenUnauthenticated() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("login", controller.login(null, model));
        assertEquals(true, model.getAttribute("passkeysEnabled"));
    }

    @Test
    void showsLoginForAnonymousToken() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertEquals("login", controller.login(anonymous, new ExtendedModelMap()));
    }

    @Test
    void redirectsHomeWhenAuthenticated() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "admin", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertEquals("redirect:/", controller.login(auth, new ExtendedModelMap()));
    }
}
