package com.scripty.service;

/**
 * Result of converting a PDF to Fountain (or plain text for songs/drafts).
 */
record PdfConversionResult(String text, boolean usedScreenplayLayout, boolean blank) {

    static PdfConversionResult empty() {
        return new PdfConversionResult("", false, true);
    }

    static PdfConversionResult of(String text, boolean usedScreenplayLayout) {
        String normalized = text == null ? "" : text;
        if (normalized.isBlank()) {
            return empty();
        }
        return new PdfConversionResult(normalized, usedScreenplayLayout, false);
    }
}
