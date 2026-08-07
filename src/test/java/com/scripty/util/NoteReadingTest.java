package com.scripty.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoteReadingTest {

    @Test
    void readsAHeadingWithoutItsHashes() {
        NoteReading.Line line = NoteReading.lineOf("## Act One");
        assertEquals(NoteReading.Kind.HEADING, line.getKind());
        assertEquals(2, line.getLevel());
        assertEquals("Act One", line.getWords());
    }

    @Test
    void readsListItemsWithoutTheirMarkers() {
        assertEquals("buy milk", NoteReading.lineOf("- buy milk").getWords());
        assertEquals("buy milk", NoteReading.lineOf("* buy milk").getWords());
        NoteReading.Line numbered = NoteReading.lineOf("12. call the composer");
        assertEquals(NoteReading.Kind.NUMBERED, numbered.getKind());
        assertEquals("call the composer", numbered.getWords());
        // The writer's own number, kept rather than counted.
        assertEquals(12, numbered.getNumber());
    }

    @Test
    void survivesANumberTooBigToHold() {
        NoteReading.Line line = NoteReading.lineOf("99999999999999. still an item");
        assertEquals(NoteReading.Kind.NUMBERED, line.getKind());
        assertEquals("still an item", line.getWords());
        assertEquals(0, line.getNumber());
    }

    @Test
    void leavesProseThatMerelyStartsWithAMarkerCharacterAlone() {
        // No space after the dash, so it is a sentence rather than an item.
        assertEquals(NoteReading.Kind.PLAIN, NoteReading.lineOf("-so it goes").getKind());
        assertEquals("-so it goes", NoteReading.lineOf("-so it goes").getWords());
        // A number that is not a marker: "1.5 seconds" is a measurement.
        assertEquals(NoteReading.Kind.PLAIN, NoteReading.lineOf("1.5 seconds").getKind());
        // Hashes with no words after them are not a heading either.
        assertEquals(NoteReading.Kind.PLAIN, NoteReading.lineOf("###").getKind());
    }

    @Test
    void countsIndentInTabsAndInIndentUnits() {
        assertEquals(0, NoteReading.lineOf("- top").getDepth());
        assertEquals(1, NoteReading.lineOf("    - nested").getDepth());
        assertEquals(1, NoteReading.lineOf("\t- nested").getDepth());
        // A part-level of stray spaces rounds down rather than promoting the line.
        assertEquals(1, NoteReading.lineOf("      - nearly two").getDepth());
    }

    @Test
    void groupsLinesIntoParagraphsOnBlankLines() {
        List<List<NoteReading.Line>> paragraphs = NoteReading.paragraphs(
                "# Act One\n\n- Jane waits\n- Bob does not come\n\n\nThe interval.");
        assertEquals(3, paragraphs.size());
        assertEquals(1, paragraphs.get(0).size());
        assertTrue(paragraphs.get(0).get(0).isHeading());
        // A run of several blank lines is one break.
        assertEquals(2, paragraphs.get(1).size());
        assertEquals("The interval.", paragraphs.get(2).get(0).getWords());
    }

    @Test
    void leavesOutTheEmptyBulletReturnLeavesBehindWithoutBreakingTheList() {
        List<List<NoteReading.Line>> paragraphs = NoteReading.paragraphs("- one\n- \n- two");
        assertEquals(1, paragraphs.size());
        assertEquals(2, paragraphs.get(0).size());
        assertEquals("one", paragraphs.get(0).get(0).getWords());
        assertEquals("two", paragraphs.get(0).get(1).getWords());
    }

    @Test
    void anEmptyNoteIsNoParagraphsAtAll() {
        assertTrue(NoteReading.paragraphs("").isEmpty());
        assertTrue(NoteReading.paragraphs("   \n\n  ").isEmpty());
        assertTrue(NoteReading.paragraphs(null).isEmpty());
    }
}
