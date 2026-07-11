package com.scripty.security;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Grants access to /actuator/prometheus when the request carries
 * {@code Authorization: Bearer <METRICS_TOKEN>}. Lets an external scraper
 * (Grafana Cloud, a Prometheus on Railway, the local docker-compose stack)
 * pull metrics without a user session. If no token is configured, the
 * endpoint is closed — it never falls open.
 */
public class MetricsTokenAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedToken;

    public MetricsTokenAuthorizationManager(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
            RequestAuthorizationContext context) {
        if (expectedToken.isEmpty()) {
            return new AuthorizationDecision(false);
        }
        String header = context.getRequest().getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return new AuthorizationDecision(false);
        }
        String presented = header.substring(BEARER_PREFIX.length()).trim();
        return new AuthorizationDecision(constantTimeEquals(expectedToken, presented));
    }

    private static boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
