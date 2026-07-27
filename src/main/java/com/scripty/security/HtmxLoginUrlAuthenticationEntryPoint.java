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

    private final boolean passkeysEnabled;

    public HtmxLoginUrlAuthenticationEntryPoint(String loginFormUrl) {
        this(loginFormUrl, false);
    }

    public HtmxLoginUrlAuthenticationEntryPoint(String loginFormUrl, boolean passkeysEnabled) {
        super(loginFormUrl);
        this.passkeysEnabled = passkeysEnabled;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Basic realm=\"Scripty API\"");
            response.setContentType("application/hal+json");
            response.getWriter().write(unauthorizedBody(request));
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

    /**
     * The challenge, with the things a signed-out caller can actually do.
     *
     * <p>Password recovery is the awkward case for a link-driven client: the
     * flow exists precisely for someone who cannot sign in, and every document
     * that would advertise it sits behind the sign-in. The challenge is the one
     * response such a caller is guaranteed to see, so the link rides on that —
     * which keeps recovery something a client follows rather than a path it has
     * to know.
     *
     * <p>{@code resetPassword} rides alongside it because of how a client
     * arrives at that step. Normally it comes back on the answer to the request
     * above, which is the only way a client that just asked for an email could
     * learn it. But a native app opened by the magic link in the email has the
     * token and never made that request — it may not even have been running —
     * so without the rel here it would be holding a token with nowhere to send
     * it, and the client would be reduced to guessing the path.
     *
     * <p>Passkey sign-in rides here for the same reason: the ceremony's options
     * endpoint is what an anonymous native client follows instead of typing a
     * password, and this challenge is the only document it sees. Advertised
     * only when passkeys are configured, like the {@code passkeys} rel on the
     * account.
     *
     * <p>Written by hand rather than through the HAL serializer, so there is no
     * curie to namespace it against; the bare relation names are the unprefixed
     * form of the same thing.
     */
    private String unauthorizedBody(HttpServletRequest request) {
        String recoveryHref = request.getContextPath() + "/api/forgot-password";
        String links = "\"forgotPassword\": {\"href\": \"" + recoveryHref + "\"}"
                + ", \"resetPassword\": {\"href\": \"" + recoveryHref + "/reset\"}";
        if (passkeysEnabled) {
            String passkeyHref = request.getContextPath() + "/api/login/passkey/options";
            links += ", \"passkeyLogin\": {\"href\": \"" + passkeyHref + "\"}";
        }
        return "{\"error\": \"unauthorized\", \"_links\": {" + links + "}}";
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api") || path.startsWith("/api/");
    }

    private static boolean isHtmxRequest(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
}
