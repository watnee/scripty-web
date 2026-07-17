package com.scripty.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FeatureFlagsTest {

    @Test
    void usesDefaultWhenPropertyIsAbsent() {
        FeatureFlags flags = new FeatureFlags(new MockEnvironment());
        for (FeatureFlag flag : FeatureFlag.values()) {
            assertEquals(flag.defaultValue(), flags.isEnabled(flag), flag.key());
        }
    }

    @Test
    void readsPropertyOverride() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(FeatureFlag.SERVICE_WORKER.propertyName(), "false");
        assertFalse(new FeatureFlags(environment).isEnabled(FeatureFlag.SERVICE_WORKER));
    }

    @Test
    void ignoresCaseAndSurroundingWhitespace() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(FeatureFlag.PASSKEYS.propertyName(), "  FALSE ");
        assertFalse(new FeatureFlags(environment).isEnabled(FeatureFlag.PASSKEYS));
    }

    @Test
    void blankPropertyFallsBackToDefault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(FeatureFlag.PASSKEYS.propertyName(), "");
        assertTrue(new FeatureFlags(environment).isEnabled(FeatureFlag.PASSKEYS));
    }

    @Test
    void rejectsValueThatIsNotTrueOrFalse() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(FeatureFlag.PASSKEYS.propertyName(), "yes");
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> new FeatureFlags(environment));
        assertTrue(thrown.getMessage().contains("FEATURE_PASSKEYS"), thrown.getMessage());
    }

    @Test
    void fixedValuesFillGapsWithDefaults() {
        FeatureFlags flags = new FeatureFlags(Map.of(FeatureFlag.SERVICE_WORKER, false));
        assertFalse(flags.isEnabled(FeatureFlag.SERVICE_WORKER));
        assertEquals(FeatureFlag.PASSKEYS.defaultValue(), flags.isEnabled(FeatureFlag.PASSKEYS));
    }

    @Test
    void derivesRailwayVariableNameFromKey() {
        assertEquals("FEATURE_SERVICE_WORKER", FeatureFlag.SERVICE_WORKER.environmentVariable());
        assertEquals("app.features.service-worker", FeatureFlag.SERVICE_WORKER.propertyName());
    }
}
