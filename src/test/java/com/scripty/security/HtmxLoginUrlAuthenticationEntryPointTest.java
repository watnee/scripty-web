package com.scripty.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.web.RedirectStrategy;

class HtmxLoginUrlAuthenticationEntryPointTest {

    private final HtmxLoginUrlAuthenticationEntryPoint entryPoint =
            new HtmxLoginUrlAuthenticationEntryPoint("/login");

    @Test
    void htmxRequestGetsUnauthorizedWithHxRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/show");
        request.addHeader("HX-Request", "true");
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request,
                response,
                new AuthenticationCredentialsNotFoundException("unauthenticated"));

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("https://example.com/login", response.getHeader("HX-Redirect"));
    }

    @Test
    void browserRequestRedirectsToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/project/show");
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        MockHttpServletResponse response = new MockHttpServletResponse();

        RedirectStrategy redirectStrategy = mock(RedirectStrategy.class);
        entryPoint.setRedirectStrategy(redirectStrategy);

        entryPoint.commence(
                request,
                response,
                new AuthenticationCredentialsNotFoundException("unauthenticated"));

        assertNull(response.getHeader("HX-Redirect"));
        verify(redirectStrategy).sendRedirect(request, response, "https://example.com/login");
    }
}
