package com.scripty.security;

import jakarta.servlet.http.HttpServletRequest;

/** Identifies /api requests, whose security failures must be JSON, never HTML or redirects. */
final class ApiRequests {

    private ApiRequests() {
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api") || path.startsWith("/api/");
    }
}
