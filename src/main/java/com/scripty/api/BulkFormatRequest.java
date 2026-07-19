package com.scripty.api;

import java.util.List;

/**
 * Applies character formatting to every named block.
 *
 * <p>The four MVC endpoints ({@code bulkSetAlign}, {@code bulkSetFont},
 * {@code bulkToggleStyle}, {@code bulkSetHighlight}) are merged into one call
 * so a client can set several at once and get a single undo checkpoint for the
 * lot. Each field is applied only when present; all four omitted is a no-op.
 *
 * <p>{@code highlight} is the exception to "omitted means leave alone" being
 * the only way to keep a value: because a blank highlight legitimately means
 * "clear the tint", pass {@code clearHighlight} to remove it and leave
 * {@code highlight} null to keep what is stored.
 *
 * <p>{@code style} is a per-block <em>toggle</em>, not a set — each block flips
 * independently, so a mixed selection comes back inverted rather than uniform.
 * That is the web behaviour and is preserved deliberately.
 */
public record BulkFormatRequest(
        List<Integer> ids,
        Integer projectId,
        String align,
        String font,
        String style,
        String highlight,
        Boolean clearHighlight) implements BulkBlockRequest {

    public boolean hasAlign() {
        return align != null;
    }

    public boolean hasFont() {
        return font != null;
    }

    public boolean hasStyle() {
        return style != null;
    }

    /** True when the caller asked to set a tint or to clear one. */
    public boolean hasHighlight() {
        return highlight != null || Boolean.TRUE.equals(clearHighlight);
    }

    /** The value to store: null clears, which is what the service expects. */
    public String resolvedHighlight() {
        return Boolean.TRUE.equals(clearHighlight) ? null : highlight;
    }

    public boolean isEmpty() {
        return !hasAlign() && !hasFont() && !hasStyle() && !hasHighlight();
    }
}
