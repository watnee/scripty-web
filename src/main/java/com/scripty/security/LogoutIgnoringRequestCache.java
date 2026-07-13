package com.scripty.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Only remember full-page navigations as the post-login destination.
 *
 * <p>Before GET logout was accepted, anonymous hits to {@code /logout} were saved and
 * then replayed after sign-in — which immediately signed the user back out (or 404'd).
 *
 * <p>Secured {@code /error} has the same trap: an anonymous error dispatch gets saved,
 * login succeeds, then the browser is sent back to {@code /error} with no error
 * attributes — Spring Boot's Whitelabel page with {@code status=999}.
 *
 * <p>Background polls and partial fetches are worse still: after logout (or a multi-tab
 * session race), live sync can save {@code /project/syncStatus} or
 * {@code /project/showScript} and send the user to JSON / a fragment instead of a real
 * page.
 */
public class LogoutIgnoringRequestCache extends HttpSessionRequestCache {

    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        if (isIgnored(request)) {
            return;
        }
        super.saveRequest(request, response);
    }

    static boolean isIgnored(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isNonPagePath(pathWithinApplication(request))) {
            return true;
        }
        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            return true;
        }
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String fetchMode = request.getHeader("Sec-Fetch-Mode");
        if (fetchMode != null && !"navigate".equalsIgnoreCase(fetchMode)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null) {
            String lower = accept.toLowerCase();
            boolean wantsJson = lower.contains("application/json") || lower.contains("application/hal+json");
            boolean wantsHtml = lower.contains("text/html");
            if (wantsJson && !wantsHtml) {
                return true;
            }
        }
        return false;
    }

    /**
     * Paths that must never be replayed after login (auth endpoints, API polls, HTMX
     * fragments). Shared with {@link LoginSuccessHandler} so stale session entries are
     * cleared even if they were saved before these rules existed.
     */
    static boolean isNonPagePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if ("/logout".equals(path) || "/error".equals(path) || "/login".equals(path)) {
            return true;
        }
        if ("/project/syncStatus".equals(path) || "/project/showScript".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/")) {
            return true;
        }
        return path.contains("Inline");
    }

    static boolean isUnsafePostLoginRedirect(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            return false;
        }
        try {
            return isNonPagePath(URI.create(redirectUrl).getPath());
        } catch (IllegalArgumentException ex) {
            return redirectUrl.contains("/logout")
                    || redirectUrl.contains("/error")
                    || redirectUrl.contains("/login")
                    || redirectUrl.contains("/syncStatus")
                    || redirectUrl.contains("/showScript")
                    || redirectUrl.contains("/api/")
                    || redirectUrl.contains("Inline");
        }
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri;
    }
}
