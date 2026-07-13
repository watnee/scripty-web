package com.scripty.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves Spring Security 6's deferred CSRF token before the controller runs.
 *
 * <p>Left deferred, the token is first read while Thymeleaf renders the nav
 * fragment's {@code _csrf} meta tags. On pages larger than Tomcat's 8KB output
 * buffer the response is committed by then, and for a first-time (sessionless)
 * visitor storing the fresh token throws "Cannot create a session after the
 * response has been committed" mid-render — the visitor gets a truncated page
 * ending in the "200 — something went wrong" error view.
 *
 * <p>Static assets are skipped so plain resource requests don't create an HTTP
 * session per request.
 */
public class CsrfTokenEagerLoadingFilter extends OncePerRequestFilter {

    private static final List<String> STATIC_PREFIXES = List.of(
            "/css/", "/js/", "/icons/", "/fonts/", "/dictionaries/", "/actuator/");

    private static final Set<String> STATIC_PATHS = Set.of(
            "/favicon.ico", "/manifest.json", "/sw.js", "/offline.html", "/offline-project.html");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (STATIC_PATHS.contains(path)) {
            return true;
        }
        return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Forces token generation (and its session) while the response is still writable.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
