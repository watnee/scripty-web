package com.scripty.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Passkey (WebAuthn) settings derived from {@code app.base-url}. Passkeys are
 * bound to a domain (the relying-party id), so they are only enabled when a
 * usable base URL is configured; without one the login page shows password
 * sign-in only.
 */
@Component
public class PasskeySettings {

    private final boolean enabled;
    private final String rpId;
    private final String origin;

    @Autowired
    public PasskeySettings(FeatureFlags featureFlags, @Value("${app.base-url:}") String baseUrl) {
        this(featureFlags.isEnabled(FeatureFlag.PASSKEYS), baseUrl);
    }

    public PasskeySettings(boolean enabled, String baseUrl) {
        String host = null;
        String parsedOrigin = null;
        if (baseUrl != null && !baseUrl.isBlank()) {
            try {
                URI uri = URI.create(baseUrl.trim());
                host = uri.getHost();
                if (host != null) {
                    parsedOrigin = uri.getScheme() + "://" + host
                            + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // Unparseable base URL → passkeys stay disabled.
            }
        }
        this.enabled = enabled && host != null;
        this.rpId = host;
        this.origin = parsedOrigin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Relying-party id: the host of {@code app.base-url}. */
    public String getRpId() {
        return rpId;
    }

    /** Allowed WebAuthn origin, e.g. {@code https://scripty.example.com}. */
    public String getOrigin() {
        return origin;
    }
}
