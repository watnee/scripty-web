package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class MusicXmlToLyricsConverterTest {

    // --- The round trip ----------------------------------------------------------

    @Test
    void writesAndReadsBackTheSameLines() throws IOException {
        String lyrics = """
                Hold the line a little longer
                Wait until the morning comes

                Nothing here belongs to us
                Nothing here belongs""";

        String back = read(SongMusicXmlWriter.write("Hold The Line",
                List.of(new SongMusicXmlWriter.Song(null, lyrics))));

        assertEquals(lyrics, back);
    }

    @Test
    void keepsTheScoreTitle() throws IOException {
        byte[] score = SongMusicXmlWriter.write("Hold The Line",
                List.of(new SongMusicXmlWriter.Song(null, "One two three")));
        assertEquals("Hold The Line", convert(score).title());
    }

    @Test
    void gathersSeveralSongsAsSectionsOfOneScore() throws IOException {
        byte[] score = SongMusicXmlWriter.write("Songs", List.of(
                new SongMusicXmlWriter.Song("First", "One two"),
                new SongMusicXmlWriter.Song("Second", "Three four")));
        String xml = new String(score, StandardCharsets.UTF_8);

        // Each song heads its own page, so the sections do not run together.
        assertTrue(xml.contains("<print new-page=\"yes\"/>"), xml);
        assertTrue(xml.contains(">First</words>"), xml);
        assertTrue(xml.contains(">Second</words>"), xml);
        assertEquals("One two\nThree four", read(score));
    }

    @Test
    void escapesWordsThatWouldBreakTheMarkup() throws IOException {
        byte[] score = SongMusicXmlWriter.write("Me & You",
                List.of(new SongMusicXmlWriter.Song(null, "rock & <roll>")));
        assertEquals("Me & You", convert(score).title());
        assertEquals("rock & <roll>", read(score));
    }

    @Test
    void writesAScoreEvenWithNothingToSay() throws IOException {
        byte[] score = SongMusicXmlWriter.write("Songs", List.of());
        assertTrue(new String(score, StandardCharsets.UTF_8).contains("<measure number=\"1\">"));
        assertEquals("", read(score));
    }

    // --- Scores from somewhere else ----------------------------------------------

    @Test
    void joinsSyllablesIntoWords() throws IOException {
        String score = partwise("""
                <measure number="1">
                  %s
                  %s
                  %s
                </measure>
                """.formatted(
                note("won", "begin"),
                note("der", "end"),
                note("ful", "single")));

        assertEquals("wonder ful", read(score.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void breaksLinesOnAMeasureWhenTheScoreSaysNothingElse() throws IOException {
        String score = partwise("""
                <measure number="1">
                  %s
                  %s
                </measure>
                <measure number="2">
                  %s
                </measure>
                """.formatted(note("hold", "single"), note("on", "single"), note("tight", "single")));

        assertEquals("hold on\ntight", read(score.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void prefersASystemBreakToAMeasureBoundary() throws IOException {
        String score = partwise("""
                <measure number="1">
                  %s
                </measure>
                <measure number="2">
                  <print new-system="yes"/>
                  %s
                </measure>
                <measure number="3">
                  %s
                </measure>
                """.formatted(note("hold", "single"), note("on", "single"), note("tight", "single")));

        // Measure 3 has no break of its own, so it stays on the line it started.
        assertEquals("hold\non tight", read(score.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void stacksVersesRatherThanInterleavingThem() throws IOException {
        String score = partwise("""
                <measure number="1">
                  <note>
                    <pitch><step>C</step><octave>5</octave></pitch>
                    <duration>1</duration>
                    <type>quarter</type>
                    <lyric number="1"><text>first</text><end-line/></lyric>
                    <lyric number="2"><text>second</text><end-line/></lyric>
                  </note>
                </measure>
                """);

        assertEquals("first\n\nsecond", read(score.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsTheSingingPartAndIgnoresTheBand() throws IOException {
        String score = """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise version="4.0">
                  <part-list>
                    <score-part id="P1"><part-name>Piano</part-name></score-part>
                    <score-part id="P2"><part-name>Voice</part-name></score-part>
                  </part-list>
                  <part id="P1">
                    <measure number="1">
                      <note><rest/><duration>4</duration><type>whole</type></note>
                    </measure>
                  </part>
                  <part id="P2">
                    <measure number="1">
                      %s
                    </measure>
                  </part>
                </score-partwise>
                """.formatted(note("sing", "single"));

        assertEquals("sing", read(score.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void survivesTheDoctypeEveryRealScoreCarries() throws IOException {
        String score = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 3.1 Partwise//EN" \
                "http://www.musicxml.org/dtds/partwise.dtd">
                <score-partwise version="3.1">
                  <work><work-title>From Finale</work-title></work>
                  <part-list><score-part id="P1"><part-name>Voice</part-name></score-part></part-list>
                  <part id="P1"><measure number="1">%s</measure></part>
                </score-partwise>
                """.formatted(note("here", "single"));

        MusicXmlToLyricsConverter.Lyrics lyrics = convert(score.getBytes(StandardCharsets.UTF_8));
        assertEquals("From Finale", lyrics.title());
        assertEquals("here", lyrics.text());
    }

    @Test
    void readsACompressedScore() throws IOException {
        String inner = partwise("<measure number=\"1\">" + note("zipped", "single") + "</measure>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zip.write("""
                    <container><rootfiles>
                      <rootfile full-path="score.xml" media-type="application/vnd.recordare.musicxml+xml"/>
                    </rootfiles></container>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("score.xml"));
            zip.write(inner.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals("zipped", read(out.toByteArray()));
    }

    @Test
    void refusesSomethingThatIsNotAScore() {
        byte[] notAScore = "<html><body>words</body></html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> convert(notAScore));
    }

    @Test
    void aScoreWithNoWordsHasNoLyric() throws IOException {
        String score = partwise("""
                <measure number="1">
                  <note><rest/><duration>4</duration><type>whole</type></note>
                </measure>
                """);
        assertEquals("", read(score.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void recognizesScoresByNameAndByContent() {
        assertTrue(MusicXmlToLyricsConverter.looksLikeMusicXml("song.musicxml", ""));
        assertTrue(MusicXmlToLyricsConverter.looksLikeMusicXml("song.mxl", ""));
        assertTrue(MusicXmlToLyricsConverter.looksLikeMusicXml("song", "application/vnd.recordare.musicxml+xml"));
        assertTrue(MusicXmlToLyricsConverter.looksLikeMusicXmlContent(
                "<score-partwise version=\"4.0\">".getBytes(StandardCharsets.UTF_8)));
        assertTrue(!MusicXmlToLyricsConverter.looksLikeMusicXml("notes.txt", "text/plain"));
        assertTrue(!MusicXmlToLyricsConverter.looksLikeMusicXmlContent(
                "<rss><channel/></rss>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void hasNoTitleWhenTheScoreDoesNot() throws IOException {
        String score = partwise("<measure number=\"1\">" + note("anon", "single") + "</measure>");
        assertNull(convert(score.getBytes(StandardCharsets.UTF_8)).title());
    }

    // --- Fixtures ----------------------------------------------------------------

    private static String read(byte[] score) throws IOException {
        return convert(score).text();
    }

    private static MusicXmlToLyricsConverter.Lyrics convert(byte[] score) throws IOException {
        return MusicXmlToLyricsConverter.convert(new ByteArrayInputStream(score));
    }

    private static String partwise(String measures) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <score-partwise version="4.0">
                  <part-list><score-part id="P1"><part-name>Voice</part-name></score-part></part-list>
                  <part id="P1">
                %s
                  </part>
                </score-partwise>
                """.formatted(measures);
    }

    private static String note(String text, String syllabic) {
        return """
                <note>
                  <pitch><step>C</step><octave>5</octave></pitch>
                  <duration>1</duration>
                  <type>quarter</type>
                  <lyric number="1">
                    <syllabic>%s</syllabic>
                    <text>%s</text>
                  </lyric>
                </note>
                """.formatted(syllabic, text);
    }
}
