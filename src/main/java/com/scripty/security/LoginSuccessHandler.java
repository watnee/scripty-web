package com.scripty.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * After login, never send the browser to auth endpoints, error pages, API polls, or
 * HTMX fragment URLs. Stale sessions can still carry those as a SavedRequest from
 * unauthenticated hits that Spring Security remembered — replaying them shows JSON,
 * partial HTML, Whitelabel status 999, or immediately logs the user out again.
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
        if (saved != null && LogoutIgnoringRequestCache.isUnsafePostLoginRedirect(saved.getRedirectUrl())) {
            requestCache.removeRequest(request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
