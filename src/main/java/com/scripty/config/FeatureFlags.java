package com.scripty.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolved state of every {@link FeatureFlag}.
 *
 * <p>Values are read once at construction, so a flag cannot change while the
 * application is running: flipping one means editing the Railway service
 * variable, which restarts the service. Callers therefore never need to worry
 * about a flag changing between two reads within a request.
 */
@Component
public class FeatureFlags {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlags.class);

    private final Map<FeatureFlag, Boolean> values;

    @Autowired
    public FeatureFlags(Environment environment) {
        this(resolveAll(environment));
        log.info("Feature flags: {}", asMap());
    }

    /** Fixed flag values, for tests. */
    public FeatureFlags(Map<FeatureFlag, Boolean> values) {
        EnumMap<FeatureFlag, Boolean> resolved = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            resolved.put(flag, values.getOrDefault(flag, flag.defaultValue()));
        }
        this.values = Collections.unmodifiableMap(resolved);
    }

    private static Map<FeatureFlag, Boolean> resolveAll(Environment environment) {
        EnumMap<FeatureFlag, Boolean> resolved = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            resolved.put(flag, resolve(environment, flag));
        }
        return resolved;
    }

    private static boolean resolve(Environment environment, FeatureFlag flag) {
        String raw = environment.getProperty(flag.propertyName());
        if (raw == null || raw.isBlank()) {
            return flag.defaultValue();
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        // Fail at startup rather than silently reading a typo as "off": a flag
        // quietly stuck off is far harder to spot than a deploy that refuses.
        throw new IllegalStateException("Feature flag " + flag.key() + " must be true or false, but "
                + flag.environmentVariable() + " is \"" + raw + "\"");
    }

    public boolean isEnabled(FeatureFlag flag) {
        return values.get(flag);
    }

    /** Resolved flags keyed by flag name, for logging and diagnostics. */
    public Map<String, Boolean> asMap() {
        Map<String, Boolean> byName = new LinkedHashMap<>();
        values.forEach((flag, enabled) -> byName.put(flag.key(), enabled));
        return Collections.unmodifiableMap(byName);
    }
}
