package com.scripty.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Never remember {@code /logout} or {@code /error} as the post-login destination.
 *
 * <p>Before GET logout was accepted, anonymous hits to {@code /logout} were saved and
 * then replayed after sign-in — which immediately signed the user back out (or 404'd).
 *
 * <p>Secured {@code /error} has the same trap: an anonymous error dispatch gets saved,
 * login succeeds, then the browser is sent back to {@code /error} with no error
 * attributes — Spring Boot's Whitelabel page with {@code status=999}.
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
        String path = pathWithinApplication(request);
        return "/logout".equals(path) || "/error".equals(path);
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
