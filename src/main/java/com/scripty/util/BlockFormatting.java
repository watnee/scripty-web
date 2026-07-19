package com.scripty.util;

import com.scripty.dto.Block;

/**
 * Normalizes the alignment/font values callers send into the canonical forms
 * stored on {@link Block} ({@code LEFT}, {@code COURIER_PRIME}, …).
 *
 * <p>Both the display spellings the editors use ("left", "Courier Prime") and
 * the canonical spellings the API reports back are accepted, so a client can
 * round-trip the value it read from a block resource.
 */
public final class BlockFormatting {

    private BlockFormatting() {
    }

    /**
     * @return the canonical alignment, or {@code null} when {@code value} is
     *         null/blank or not one of left/center/right.
     */
    public static String normalizeAlign(String value) {
        String canonical = canonicalize(value);
        return canonical != null && Block.TEXT_ALIGNS.contains(canonical) ? canonical : null;
    }

    /**
     * @return the canonical font, or {@code null} when {@code value} is
     *         null/blank or not one of the three fonts the editor offers.
     */
    public static String normalizeFont(String value) {
        String canonical = canonicalize(value);
        return canonical != null && Block.FONTS.contains(canonical) ? canonical : null;
    }

    private static String canonicalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase().replaceAll("[\\s-]+", "_");
    }
}
