package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class LoginControllerTest {

    private final LoginController controller = new LoginController();

    @Test
    void showsLoginWhenUnauthenticated() {
        assertEquals("login", controller.login(null));
    }

    @Test
    void showsLoginForAnonymousToken() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertEquals("login", controller.login(anonymous));
    }

    @Test
    void redirectsHomeWhenAuthenticated() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "admin", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        assertEquals("redirect:/", controller.login(auth));
    }
}
