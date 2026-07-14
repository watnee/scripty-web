package com.scripty.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * For HTMX requests, send {@code HX-Redirect} instead of a 302 that HTMX would
 * follow and swap into the current page (leaving the URL on e.g. {@code /project/show}
 * while showing the login form).
 *
 * <p>For {@code /api} requests, answer 401 with a {@code WWW-Authenticate} challenge
 * instead of redirecting: native clients (e.g. the SwiftUI app) authenticate with
 * HTTP Basic and would otherwise follow the redirect into the login page's HTML.
 */
public class HtmxLoginUrlAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {

    public HtmxLoginUrlAuthenticationEntryPoint(String loginFormUrl) {
        super(loginFormUrl);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"Scripty API\"");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"unauthorized\"}");
            return;
        }
        if (isHtmxRequest(request)) {
            String loginUrl = buildRedirectUrlToLoginPage(request, response, authException);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("HX-Redirect", loginUrl);
            return;
        }
        super.commence(request, response, authException);
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api") || path.startsWith("/api/");
    }

    private static boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
