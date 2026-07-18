package com.scripty.service;

import java.util.Locale;

import com.scripty.dto.Block;
import com.scripty.dto.User;

/**
 * Per-user control over which screenplay element types are auto-capitalized.
 *
 * The editor renders capitalization with CSS ({@code text-transform: uppercase}),
 * so nothing is lost by turning a type off. Exporters bake the case into the
 * output, so they consult this to decide whether to call {@code toUpperCase}.
 *
 * Character and dual dialogue share one flag: they are the same element wearing
 * two layouts, and splitting them would let a script export inconsistently.
 */
public record CapitalizationPreferences(
        boolean scene,
        boolean character,
        boolean transition,
        boolean shot) {

    /** Historic behavior — every uppercased type stays uppercased. */
    public static final CapitalizationPreferences ALL = new CapitalizationPreferences(true, true, true, true);

    public static CapitalizationPreferences from(User user) {
        if (user == null) {
            return ALL;
        }
        return new CapitalizationPreferences(
                user.isAutoCapsScene(),
                user.isAutoCapsCharacter(),
                user.isAutoCapsTransition(),
                user.isAutoCapsShot());
    }

    /** True when blocks of {@code blockType} should be rendered uppercase. */
    public boolean appliesTo(String blockType) {
        if (blockType == null) {
            return false;
        }
        return switch (blockType) {
            case Block.TYPE_SCENE -> scene;
            case Block.TYPE_CHARACTER, Block.TYPE_DUAL_DIALOGUE -> character;
            case Block.TYPE_TRANSITION -> transition;
            case Block.TYPE_SHOT -> shot;
            default -> false;
        };
    }

    /** Uppercases {@code text} only if {@code blockType} is enabled. */
    public String apply(String text, String blockType) {
        if (text == null) {
            return null;
        }
        return appliesTo(blockType) ? text.toUpperCase(Locale.ROOT) : text;
    }

    /**
     * Space-separated {@code scripty-no-caps-*} classes for the {@code <html>}
     * element, so the first paint already matches the user's preference instead
     * of flashing uppercase until the toggle script runs.
     */
    public String htmlClasses() {
        StringBuilder sb = new StringBuilder();
        if (!scene) {
            sb.append("scripty-no-caps-scene ");
        }
        if (!character) {
            sb.append("scripty-no-caps-character ");
        }
        if (!transition) {
            sb.append("scripty-no-caps-transition ");
        }
        if (!shot) {
            sb.append("scripty-no-caps-shot ");
        }
        return sb.toString().trim();
    }
}
