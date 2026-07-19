package com.scripty.config;

import java.util.Locale;

/**
 * Feature flags the application understands. Each constant is the single
 * declaration of a flag: its name, its default, and (by derivation) the
 * property and environment variable that override it.
 *
 * <p>Flags resolve once at startup from the Railway service variable named by
 * {@link #environmentVariable()}, so flipping one in the Railway dashboard
 * restarts the service. See {@code docs/FEATURE_FLAGS.md}.
 *
 * <p>Flags are for behaviour that is turned on or off. Settings that carry a
 * value (credentials, URLs, {@code app.asset-version}) stay ordinary config.
 */
public enum FeatureFlag {

    /** Passkey (WebAuthn) sign-in. Also requires a usable {@code app.base-url}. */
    PASSKEYS("passkeys", true),

    /**
     * Service worker registration. Caches static assets and public offline
     * shells only; project HTML lives in IndexedDB.
     */
    SERVICE_WORKER("service-worker", true),

    /**
     * Managing invitations over the REST API.
     *
     * <p>Default off, unlike every other flag here, because turning it on
     * widens who can make the server send email. The web forms have always been
     * able to, but a form is driven by a person and an endpoint can be driven
     * by a script; the endpoints are rate limited for that reason, and this
     * flag means the surface does not appear at all until someone decides it
     * should.
     */
    API_INVITATIONS("api-invitations", false);

    private final String key;
    private final boolean defaultValue;

    FeatureFlag(String key, boolean defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    /** Kebab-case flag name, e.g. {@code service-worker}. */
    public String key() {
        return key;
    }

    /** Value used when neither the property nor the environment variable is set. */
    public boolean defaultValue() {
        return defaultValue;
    }

    /** Spring property backing this flag, e.g. {@code app.features.service-worker}. */
    public String propertyName() {
        return "app.features." + key;
    }

    /** Railway service variable for this flag, e.g. {@code FEATURE_SERVICE_WORKER}. */
    public String environmentVariable() {
        return "FEATURE_" + key.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
