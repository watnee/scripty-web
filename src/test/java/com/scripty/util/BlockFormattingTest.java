package com.scripty.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class BlockFormattingTest {

    @Test
    void acceptsTheDisplaySpellingsClientsSend() {
        assertEquals("LEFT", BlockFormatting.normalizeAlign("left"));
        assertEquals("CENTER", BlockFormatting.normalizeAlign("center"));
        assertEquals("RIGHT", BlockFormatting.normalizeAlign("right"));
        assertEquals("COURIER_PRIME", BlockFormatting.normalizeFont("Courier Prime"));
        assertEquals("ARIAL", BlockFormatting.normalizeFont("Arial"));
        assertEquals("TIMES_NEW_ROMAN", BlockFormatting.normalizeFont("Times New Roman"));
    }

    @Test
    void acceptsTheCanonicalSpellingsTheApiReportsBack() {
        assertEquals("CENTER", BlockFormatting.normalizeAlign("CENTER"));
        assertEquals("TIMES_NEW_ROMAN", BlockFormatting.normalizeFont("TIMES_NEW_ROMAN"));
    }

    @Test
    void rejectsUnknownAndBlankValues() {
        assertNull(BlockFormatting.normalizeAlign("justify"));
        assertNull(BlockFormatting.normalizeAlign("  "));
        assertNull(BlockFormatting.normalizeAlign(null));
        assertNull(BlockFormatting.normalizeFont("Comic Sans"));
        assertNull(BlockFormatting.normalizeFont(null));
    }
}
