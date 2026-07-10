package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

class LoginSuccessHandlerTest {

    @Test
    void ignoresStaleLogoutSavedRequestAndGoesHome() throws Exception {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        LoginSuccessHandler handler = new LoginSuccessHandler(cache);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate a session that still has /logout as the remembered destination.
        MockHttpServletRequest prior = new MockHttpServletRequest("GET", "/logout");
        prior.setScheme("https");
        prior.setServerName("example.com");
        prior.setServerPort(443);
        prior.setSession(request.getSession(true));
        new HttpSessionRequestCache().saveRequest(prior, response);

        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "admin");
        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals("/", response.getRedirectedUrl());
        assertNull(cache.getRequest(request, response));
    }

    @Test
    void ignoresStaleErrorSavedRequestAndGoesHome() throws Exception {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        LoginSuccessHandler handler = new LoginSuccessHandler(cache);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Secured /error gets saved for anonymous users; replaying it after login
        // renders Spring Boot's Whitelabel page with status=999.
        MockHttpServletRequest prior = new MockHttpServletRequest("GET", "/error");
        prior.setScheme("https");
        prior.setServerName("example.com");
        prior.setServerPort(443);
        prior.setSession(request.getSession(true));
        new HttpSessionRequestCache().saveRequest(prior, response);

        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "admin");
        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals("/", response.getRedirectedUrl());
        assertNull(cache.getRequest(request, response));
    }

    @Test
    void preservesNormalSavedRequest() throws Exception {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        LoginSuccessHandler handler = new LoginSuccessHandler(cache);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest prior = new MockHttpServletRequest("GET", "/project/show");
        prior.setScheme("https");
        prior.setServerName("example.com");
        prior.setServerPort(443);
        prior.setQueryString("id=42");
        prior.setSession(request.getSession(true));
        cache.saveRequest(prior, response);

        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "admin");
        handler.onAuthenticationSuccess(request, response, auth);

        assertTrue(response.getRedirectedUrl().contains("/project/show"));
        assertTrue(response.getRedirectedUrl().contains("id=42"));
    }

    @Test
    void requestCacheDoesNotSaveLogout() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }

    @Test
    void requestCacheDoesNotSaveError() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }
}
