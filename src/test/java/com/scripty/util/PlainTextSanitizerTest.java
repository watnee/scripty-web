package com.scripty.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PlainTextSanitizerTest {

    @Test
    void sanitizeReturnsNullForNull() {
        assertNull(PlainTextSanitizer.sanitize(null));
    }

    @Test
    void sanitizeStripsHtmlTagsButKeepsText() {
        // Tags are removed; inner text remains as inert plain text.
        assertEquals(
                "Hello alert(1)world",
                PlainTextSanitizer.sanitize("Hello <script>alert(1)</script>world"));
        assertEquals(
                "click me",
                PlainTextSanitizer.sanitize("<a href=\"javascript:alert(1)\">click me</a>"));
        assertEquals(
                "bold",
                PlainTextSanitizer.sanitize("<b onclick=\"evil()\">bold</b>"));
    }

    @Test
    void sanitizePreservesFountainAndNewlines() {
        String fountain = "INT. OFFICE - DAY\n\nJOHN\nHello.\n\n> CUT TO:";
        assertEquals(fountain, PlainTextSanitizer.sanitize(fountain));
    }

    @Test
    void sanitizePreservesAngleBracketsThatAreNotTags() {
        // Fountain forced elements and comparisons should survive.
        assertEquals("A < B", PlainTextSanitizer.sanitize("A < B"));
        assertEquals("> CUT TO:", PlainTextSanitizer.sanitize("> CUT TO:"));
        assertEquals("@JOHN", PlainTextSanitizer.sanitize("@JOHN"));
    }

    @Test
    void sanitizeRemovesControlCharactersButKeepsTabAndNewline() {
        assertEquals(
                "a\tb\nc",
                PlainTextSanitizer.sanitize("a\tb\nc\u0000\u0007"));
    }

    @Test
    void sanitizeSingleLineCollapsesNewlinesAndStripsTags() {
        assertEquals(
                "Title xline",
                PlainTextSanitizer.sanitizeSingleLine("Title\n<script>x</script>line"));
        assertNull(PlainTextSanitizer.sanitizeSingleLine(null));
    }
}
