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
 * <p>{@code highlight} and {@code font} are the exceptions to "omitted means
 * leave alone" being the only way to keep a value: because a blank value
 * legitimately means "clear it back to the default", pass {@code clearHighlight}
 * / {@code clearFont} to remove one and leave the value field null to keep what
 * is stored. (A blank {@code font} cannot double as "clear" the way it does for
 * the lenient MVC endpoint, because this endpoint rejects an unrecognised font
 * outright.)
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
        Boolean clearHighlight,
        Boolean clearFont) implements BulkBlockRequest {

    public boolean hasAlign() {
        return align != null;
    }

    /** True when the caller asked to set a font or to clear one. */
    public boolean hasFont() {
        return font != null || Boolean.TRUE.equals(clearFont);
    }

    /** The value to store: null clears, which is what the service expects. */
    public String resolvedFont() {
        return Boolean.TRUE.equals(clearFont) ? null : font;
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
