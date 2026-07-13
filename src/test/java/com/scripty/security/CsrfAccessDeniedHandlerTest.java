package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class CsrfAccessDeniedHandlerTest {

    private final CsrfAccessDeniedHandler handler = new CsrfAccessDeniedHandler();

    @Test
    void redirectsHtmlCsrfFailureToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new MissingCsrfTokenException(null));

        assertEquals(302, response.getStatus());
        assertEquals("/login?csrf_error=1", response.getRedirectedUrl());
        assertNull(response.getHeader("HX-Redirect"));
    }

    @Test
    void redirectsInvalidCsrfTokenToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        DefaultCsrfToken expected = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "expected");

        handler.handle(request, response, new InvalidCsrfTokenException(expected, "actual"));

        assertEquals("/login?csrf_error=1", response.getRedirectedUrl());
    }

    @Test
    void usesHxRedirectForHtmxCsrfFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/block/createInline");
        request.addHeader("Accept", "text/html");
        request.addHeader("HX-Request", "true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new MissingCsrfTokenException(null));

        assertEquals(403, response.getStatus());
        assertEquals("/login?csrf_error=1", response.getHeader("HX-Redirect"));
        assertNull(response.getRedirectedUrl());
    }

    @Test
    void doesNotRedirectNonCsrfAccessDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/list");
        request.addHeader("Accept", "text/html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertFalse(response.isCommitted() && response.getRedirectedUrl() != null
                && response.getRedirectedUrl().contains("csrf_error"));
        assertEquals(403, response.getStatus());
    }
}
