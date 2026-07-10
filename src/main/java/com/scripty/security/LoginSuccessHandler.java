package com.scripty.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * After login, never send the browser to {@code /logout} or {@code /error}. Stale
 * sessions can still carry those as a SavedRequest from unauthenticated hits that
 * Spring Security remembered — replaying {@code /error} shows Whitelabel status 999.
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache;

    public LoginSuccessHandler(RequestCache requestCache) {
        this.requestCache = requestCache;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(false);
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null && isUnsafePostLoginRedirect(saved.getRedirectUrl())) {
            requestCache.removeRequest(request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private static boolean isUnsafePostLoginRedirect(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isEmpty()) {
            return false;
        }
        try {
            String path = URI.create(redirectUrl).getPath();
            return path != null && (path.endsWith("/logout") || path.endsWith("/error"));
        } catch (IllegalArgumentException ex) {
            return redirectUrl.contains("/logout") || redirectUrl.contains("/error");
        }
    }
}
