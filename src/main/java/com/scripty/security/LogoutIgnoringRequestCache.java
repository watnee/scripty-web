package com.scripty.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/**
 * Never remember {@code /logout} as the post-login destination. Before GET logout
 * was accepted, anonymous hits to {@code /logout} were saved and then replayed
 * after sign-in — which immediately signed the user back out (or 404'd).
 */
public class LogoutIgnoringRequestCache extends HttpSessionRequestCache {

    @Override
    public void saveRequest(HttpServletRequest request, HttpServletResponse response) {
        if (isLogout(request)) {
            return;
        }
        super.saveRequest(request, response);
    }

    static boolean isLogout(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return "/logout".equals(uri);
    }
}
