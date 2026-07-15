package com.scripty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.service.ApiTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * Replaces Spring Security's default {@code /login/webauthn} success response.
 * Every client still gets the {@code {"authenticated":true,"redirectUrl":"/"}}
 * body the framework's webauthn.js expects. Native clients additionally send an
 * {@code X-Scripty-Client} header, and for those we mint an API token and return
 * it in the body so the stateless client can authenticate later requests with
 * {@code Authorization: Bearer}. Browsers omit the header and never see a token.
 */
public class TokenIssuingWebAuthnSuccessHandler implements AuthenticationSuccessHandler {

    static final String NATIVE_CLIENT_HEADER = "X-Scripty-Client";

    private final ApiTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final RequestCache requestCache;

    public TokenIssuingWebAuthnSuccessHandler(ApiTokenService tokenService,
            ObjectMapper objectMapper, RequestCache requestCache) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.requestCache = requestCache;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("redirectUrl", resolveRedirectUrl(request, response));

        String client = request.getHeader(NATIVE_CLIENT_HEADER);
        if (client != null && !client.isBlank()) {
            String username = authentication.getName();
            body.put("token", tokenService.issue(username, "Passkey sign-in (" + client + ")"));
            body.put("username", username);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }

    /** Send the browser back to the page it was trying to reach, else the root. */
    private String resolveRedirectUrl(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved != null) {
            requestCache.removeRequest(request, response);
            return saved.getRedirectUrl();
        }
        return request.getContextPath() + "/";
    }
}
