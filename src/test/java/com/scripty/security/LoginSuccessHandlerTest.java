package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void ignoresStaleSyncStatusSavedRequestAndGoesHome() throws Exception {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        LoginSuccessHandler handler = new LoginSuccessHandler(cache);

        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Multi-tab race: live sync poll after logout saves JSON endpoint into the new session.
        MockHttpServletRequest prior = new MockHttpServletRequest("GET", "/project/syncStatus");
        prior.setScheme("https");
        prior.setServerName("example.com");
        prior.setServerPort(443);
        prior.setQueryString("id=5&since=12");
        prior.setSession(request.getSession(true));
        new HttpSessionRequestCache().saveRequest(prior, response);

        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "admin");
        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals("/", response.getRedirectedUrl());
        assertNull(cache.getRequest(request, response));
    }

    @Test
    void ignoresStaleShowScriptSavedRequestAndGoesHome() throws Exception {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        LoginSuccessHandler handler = new LoginSuccessHandler(cache);

        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest prior = new MockHttpServletRequest("GET", "/project/showScript");
        prior.setScheme("https");
        prior.setServerName("example.com");
        prior.setServerPort(443);
        prior.setQueryString("id=5");
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

    @Test
    void requestCacheDoesNotSaveSyncStatusPoll() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/syncStatus");
        request.setQueryString("id=5&since=1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }

    @Test
    void requestCacheDoesNotSaveShowScriptFragment() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/showScript");
        request.setQueryString("id=5");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }

    @Test
    void requestCacheDoesNotSaveHtmxRequest() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/list");
        request.addHeader("HX-Request", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }

    @Test
    void requestCacheDoesNotSaveFetchCorsMode() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/list");
        request.addHeader("Sec-Fetch-Mode", "cors");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }

    @Test
    void requestCacheSavesBrowserNavigation() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/show");
        request.setQueryString("id=42");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertTrue(cache.getRequest(request, response) != null);
    }

    @Test
    void requestCacheDoesNotSaveInlineFragment() {
        LogoutIgnoringRequestCache cache = new LogoutIgnoringRequestCache();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/block/showInline");
        request.setQueryString("id=9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        cache.saveRequest(request, response);

        assertNull(cache.getRequest(request, response));
    }

    @Test
    void unsafeRedirectDetection() {
        assertTrue(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect("https://example.com/logout"));
        assertTrue(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect("https://example.com/error"));
        assertTrue(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect(
                "https://example.com/project/syncStatus?id=5&since=1"));
        assertTrue(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect(
                "https://example.com/project/showScript?id=5"));
        assertTrue(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect(
                "https://example.com/block/editInline?id=3"));
        assertFalse(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect(
                "https://example.com/project/show?id=42"));
        assertFalse(LogoutIgnoringRequestCache.isUnsafePostLoginRedirect(
                "https://example.com/project/list"));
    }

    private static MockHttpServletRequest loginRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        return request;
    }
}
