package com.scripty.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes lyrics out as a score, so a song can be opened in MuseScore, Finale,
 * Sibelius or Dorico and set to music.
 *
 * <p>Scripty knows the words and nothing else, so the notes here are scaffolding:
 * a single vocal staff of quarter notes on one pitch, one note per word. That is
 * what a composer wants to receive — the syllables already laid under a staff,
 * ready to be dragged onto real pitches — and it is the same thing notation
 * programs produce when you paste a block of text into a lyric line.
 *
 * <p>Where the writer's lines end is carried as {@code <end-line/>}, and stanza
 * breaks as {@code <end-paragraph/>}, which is what lets
 * {@link MusicXmlToLyricsConverter} read the shape of the lyric back out
 * unchanged even after a notation program has rewritten everything else.
 */
final class SongMusicXmlWriter {

    static final String CONTENT_TYPE = "application/vnd.recordare.musicxml+xml";
    static final String EXTENSION = "musicxml";

    private static final int BEATS_PER_MEASURE = 4;
    private static final String PART_ID = "P1";
    private static final String PART_NAME = "Voice";

    /** Where an unset melody sits: comfortable in both treble and vocal reading. */
    private static final String PITCH_STEP = "C";
    private static final int PITCH_OCTAVE = 5;

    private SongMusicXmlWriter() {
    }

    /** One song to lay out: its heading and its lines, blank lines included. */
    record Song(String title, String lyrics) {
    }

    /**
     * @param scoreTitle what the score calls itself — the song's title for a
     *                   single song, the songbook's name for several
     */
    static byte[] write(String scoreTitle, List<Song> songs) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\"")
                .append(" \"http://www.musicxml.org/dtds/partwise.dtd\">\n");
        xml.append("<score-partwise version=\"4.0\">\n");
        xml.append("  <work>\n    <work-title>").append(escape(scoreTitle))
                .append("</work-title>\n  </work>\n");
        xml.append("  <identification>\n    <encoding>\n      <software>Scripty</software>\n")
                .append("      <supports element=\"print\" attribute=\"new-page\" type=\"yes\" value=\"yes\"/>\n")
                .append("    </encoding>\n  </identification>\n");
        xml.append("  <part-list>\n    <score-part id=\"").append(PART_ID).append("\">\n")
                .append("      <part-name>").append(PART_NAME).append("</part-name>\n")
                .append("    </score-part>\n  </part-list>\n");
        xml.append("  <part id=\"").append(PART_ID).append("\">\n");
        appendMeasures(xml, songs);
        xml.append("  </part>\n");
        xml.append("</score-partwise>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendMeasures(StringBuilder xml, List<Song> songs) {
        Measures measures = new Measures(xml);
        boolean first = true;
        for (Song song : songs) {
            // Each song starts its own page, and says its name above the staff:
            // one score of several songs reads as sections, which is the only
            // shape MusicXML has for them.
            measures.startSong(song.title(), !first);
            first = false;
            appendLyrics(measures, song.lyrics());
        }
        measures.finish();
    }

    private static void appendLyrics(Measures measures, String lyrics) {
        boolean pendingStanzaBreak = false;
        for (String line : (lyrics == null ? "" : lyrics).split("\n", -1)) {
            List<String> words = words(line);
            if (words.isEmpty()) {
                // A blank line is a stanza break, which belongs on the last
                // note of the line before it rather than on a note of its own.
                pendingStanzaBreak = true;
                continue;
            }
            if (pendingStanzaBreak) {
                measures.endStanzaOnLastNote();
                pendingStanzaBreak = false;
            }
            for (int i = 0; i < words.size(); i++) {
                measures.note(words.get(i), i == words.size() - 1);
            }
        }
    }

    private static List<String> words(String line) {
        List<String> words = new ArrayList<>();
        for (String word : line.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /**
     * Fills measures four beats at a time. A line never runs into the next one:
     * the rest of its measure is padded with rests, which is how a singer's part
     * is engraved anyway.
     */
    private static final class Measures {
        private final StringBuilder xml;
        private final StringBuilder measure = new StringBuilder();
        private int number = 0;
        private int beats = 0;
        private boolean wroteAttributes = false;
        private String pendingPrint = null;
        private String pendingDirection = null;
        /**
         * Where a break would go on the last note written, and whether one is
         * owed. Which break it is — a line or a stanza — is not known until the
         * next line is read, so the marker is written late rather than guessed
         * at and rewritten.
         */
        private int lastLyricEnd = -1;
        private boolean lineEndOwed = false;

        Measures(StringBuilder xml) {
            this.xml = xml;
        }

        void startSong(String title, boolean newPage) {
            settle("          <end-line/>\n");
            if (newPage) {
                pendingPrint = "      <print new-page=\"yes\"/>\n";
            }
            if (title != null && !title.isBlank()) {
                pendingDirection = "      <direction placement=\"above\">\n"
                        + "        <direction-type>\n"
                        + "          <words font-weight=\"bold\" font-size=\"14\">"
                        + escape(title) + "</words>\n"
                        + "        </direction-type>\n"
                        + "      </direction>\n";
            }
            // Opened now rather than at the first word, so a song with no
            // lyrics still prints its heading instead of donating it to the
            // next song along.
            open();
        }

        void note(String word, boolean endsLine) {
            settle("          <end-line/>\n");
            open();
            if (beats == BEATS_PER_MEASURE) {
                close();
                open();
            }
            measure.append("      <note>\n")
                    .append("        <pitch>\n          <step>").append(PITCH_STEP)
                    .append("</step>\n          <octave>").append(PITCH_OCTAVE)
                    .append("</octave>\n        </pitch>\n")
                    .append("        <duration>1</duration>\n")
                    .append("        <voice>1</voice>\n")
                    .append("        <type>quarter</type>\n")
                    .append("        <lyric number=\"1\">\n")
                    .append("          <syllabic>single</syllabic>\n")
                    .append("          <text>").append(escape(word)).append("</text>\n");
            lastLyricEnd = measure.length();
            measure.append("        </lyric>\n")
                    .append("      </note>\n");
            beats++;
            lineEndOwed = endsLine;
        }

        /** The line that just ended also ended a stanza. */
        void endStanzaOnLastNote() {
            settle("          <end-paragraph/>\n");
        }

        /**
         * Writes the break owed to the previous line, if any, and closes the
         * measure behind it so the next line starts on a bar of its own.
         */
        private void settle(String element) {
            if (!lineEndOwed) {
                return;
            }
            lineEndOwed = false;
            if (lastLyricEnd >= 0 && lastLyricEnd <= measure.length()) {
                measure.insert(lastLyricEnd, element);
            }
            close();
        }

        private void open() {
            if (measure.length() > 0) {
                return;
            }
            number++;
            measure.setLength(0);
            beats = 0;
            lastLyricEnd = -1;
            measure.append("    <measure number=\"").append(number).append("\">\n");
            if (pendingPrint != null) {
                measure.append(pendingPrint);
                pendingPrint = null;
            }
            if (!wroteAttributes) {
                measure.append("""
                              <attributes>
                                <divisions>1</divisions>
                                <key>
                                  <fifths>0</fifths>
                                </key>
                                <time>
                                  <beats>4</beats>
                                  <beat-type>4</beat-type>
                                </time>
                                <clef>
                                  <sign>G</sign>
                                  <line>2</line>
                                </clef>
                              </attributes>
                        """);
                wroteAttributes = true;
            }
            if (pendingDirection != null) {
                measure.append(pendingDirection);
                pendingDirection = null;
            }
        }

        /** Pads the measure out to a full bar and writes it. */
        private void close() {
            if (measure.length() == 0) {
                return;
            }
            while (beats < BEATS_PER_MEASURE) {
                measure.append("      <note>\n")
                        .append("        <rest/>\n")
                        .append("        <duration>1</duration>\n")
                        .append("        <voice>1</voice>\n")
                        .append("        <type>quarter</type>\n")
                        .append("      </note>\n");
                beats++;
            }
            measure.append("    </measure>\n");
            xml.append(measure);
            measure.setLength(0);
            beats = 0;
            lastLyricEnd = -1;
        }

        /**
         * A score with no measures at all is not a score any reader will open,
         * so an empty songbook still gets one bar of silence.
         */
        void finish() {
            settle("          <end-line/>\n");
            close();
            if (number == 0) {
                open();
                close();
            }
        }
    }

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
