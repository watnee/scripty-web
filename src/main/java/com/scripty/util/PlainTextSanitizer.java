package com.scripty.util;

import java.util.regex.Pattern;

/**
 * Defense-in-depth for user-authored plain text (blocks, documents, titles, etc.).
 * Content is stored and displayed as plain text / Fountain — not HTML — so markup
 * and control characters that could enable XSS if ever rendered unsafely are stripped.
 */
public final class PlainTextSanitizer {

    private static final Pattern HTML_TAG = Pattern.compile(
            "</?[a-zA-Z][^>]*>",
            Pattern.DOTALL);
    private static final Pattern CONTROL_CHARS = Pattern.compile(
            "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private PlainTextSanitizer() {
    }

    /**
     * Sanitize multi-line plain text (block content, documents, contact info).
     * Preserves newlines and tabs; strips HTML tags and other control characters.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String value = CONTROL_CHARS.matcher(input).replaceAll("");
        value = HTML_TAG.matcher(value).replaceAll("");
        return value;
    }

    /**
     * Sanitize a single-line field (titles, names, tags). Also collapses newlines.
     */
    public static String sanitizeSingleLine(String input) {
        String value = sanitize(input);
        if (value == null) {
            return null;
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
