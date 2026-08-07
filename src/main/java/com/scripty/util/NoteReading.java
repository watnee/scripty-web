package com.scripty.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A note as it is read rather than typed: its plain text grouped into
 * paragraphs, each line classified by the prefix it carries.
 *
 * <p>A screenplay is read by element type and a song by its lines; a note is
 * prose, and what a note has instead of element types is the handful of
 * prefixes the editor's formatting row maintains while it is being written —
 * {@code #} for a heading, {@code -} and {@code 1.} for a list. Reading them
 * back is what lets the reading page set a heading as a heading instead of
 * showing the hash.
 *
 * <p>Display only, and worth being plain about: nothing here rewrites a note.
 * The row the database holds still says "# Act One", it still says that in the
 * editor, and it still says that when the note is inserted into a screenplay.
 * This is the same posture the script reader takes when it draws a scene
 * heading in bold — the document is unchanged, the setting of it is not.
 *
 * <p>Text in, values out, so the grouping can be checked without a page. The
 * same rules run in the browser inside {@code read-aloud.js}, which has to
 * decide what a note <em>says</em>; the two must agree about what a paragraph
 * is and which lines are headings, because a note read aloud and a note read on
 * screen are the same note.
 */
public final class NoteReading {

    /** What a line turned out to be, once its marker is off. */
    public enum Kind {
        HEADING,
        BULLET,
        NUMBERED,
        PLAIN
    }

    /** One line of a note: what it is, how deep it sits, and what it says. */
    public static final class Line {
        private final Kind kind;
        private final int level;
        private final int depth;
        private final int number;
        private final String words;

        Line(Kind kind, int level, int depth, int number, String words) {
            this.kind = kind;
            this.level = level;
            this.depth = depth;
            this.number = number;
            this.words = words;
        }

        public Kind getKind() {
            return kind;
        }

        /** Heading level, 1–6; 0 for everything else. */
        public int getLevel() {
            return level;
        }

        /** How deeply a list item or plain line is indented, in indent units. */
        public int getDepth() {
            return depth;
        }

        /**
         * A numbered item's own number, as the writer wrote it; 0 for every
         * other kind. Kept rather than counted, because the editor already
         * maintains the numbering — a reading that counted for itself would
         * quietly disagree with the note it is a reading of.
         */
        public int getNumber() {
            return number;
        }

        /** What the line actually says, with its marker off. */
        public String getWords() {
            return words;
        }

        public boolean isHeading() {
            return kind == Kind.HEADING;
        }

        public boolean isListItem() {
            return kind == Kind.BULLET || kind == Kind.NUMBERED;
        }
    }

    /** Four spaces, which is what the editor's Tab writes. */
    private static final int INDENT_UNIT = 4;

    private NoteReading() {
    }

    /**
     * The note's lines, grouped into paragraphs.
     *
     * <p>A blank line is a paragraph break rather than a line with no words in
     * it — the rule a song applies to a verse — so a run of several blanks is
     * one break, and the reading page spaces paragraphs instead of drawing empty
     * rows. Line breaks <em>inside</em> a paragraph are kept exactly as typed: a
     * note is plain text, and a writer who broke a line meant to.
     *
     * <p>A line that is nothing but a marker — the empty bullet Return leaves
     * waiting for the next item — is left out rather than drawn as a dot with no
     * words beside it. It is not a paragraph break either: the writer was in the
     * middle of a list, not between two of them.
     *
     * <p>An empty note is no paragraphs at all, which is what the page's
     * "nothing to read" is asked against.
     */
    public static List<List<Line>> paragraphs(String text) {
        List<List<Line>> built = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        if (text == null) {
            return built;
        }
        for (String raw : text.split("\r\n|\r|\n", -1)) {
            if (raw.trim().isEmpty()) {
                if (!current.isEmpty()) {
                    built.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            Line line = lineOf(raw);
            if (!line.getWords().trim().isEmpty()) {
                current.add(line);
            }
        }
        if (!current.isEmpty()) {
            built.add(current);
        }
        return built;
    }

    /** One line, classified by the prefix it carries. A line holds at most one. */
    public static Line lineOf(String raw) {
        String value = raw == null ? "" : raw;
        String indent = leadingWhitespace(value);
        String rest = value.substring(indent.length());
        int depth = depthOf(indent);

        String marker = listMarker(rest);
        if (marker != null) {
            String body = rest.substring(marker.length()).replaceFirst("^\\s+", "");
            boolean numbered = marker.endsWith(".");
            // A note is whatever the writer typed, and "99999999999999. item" is
            // a thing a person can type. A number too big to hold is still an
            // item; it just does not get to say which one.
            int number = numbered ? parseNumber(marker.substring(0, marker.length() - 1)) : 0;
            return new Line(numbered ? Kind.NUMBERED : Kind.BULLET, 0, depth, number, body);
        }

        int hashes = 0;
        while (hashes < rest.length() && rest.charAt(hashes) == '#' && hashes < 6) {
            hashes++;
        }
        if (hashes > 0 && hashes < rest.length() && Character.isWhitespace(rest.charAt(hashes))) {
            return new Line(Kind.HEADING, hashes, depth, 0, rest.substring(hashes).trim());
        }
        return new Line(Kind.PLAIN, 0, depth, 0, rest);
    }

    /**
     * The list marker a line opens with — "-", "*" or "12." — or null. A marker
     * only counts when whitespace follows it, so a line reading "-so it goes" is
     * prose and "1.5 seconds" is not an item.
     */
    private static String listMarker(String rest) {
        if (rest.isEmpty()) {
            return null;
        }
        char first = rest.charAt(0);
        if ((first == '-' || first == '*') && rest.length() > 1 && Character.isWhitespace(rest.charAt(1))) {
            return String.valueOf(first);
        }
        int digits = 0;
        while (digits < rest.length() && Character.isDigit(rest.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits + 1 < rest.length()
                && rest.charAt(digits) == '.'
                && Character.isWhitespace(rest.charAt(digits + 1))) {
            return rest.substring(0, digits + 1);
        }
        return null;
    }

    private static int parseNumber(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String leadingWhitespace(String value) {
        int i = 0;
        while (i < value.length() && (value.charAt(i) == ' ' || value.charAt(i) == '\t')) {
            i++;
        }
        return value.substring(0, i);
    }

    /**
     * How deeply a line is nested, from the whitespace in front of it. A tab is
     * one level; spaces go by the indent unit, and a part-level of stray spaces
     * rounds down rather than promoting the line.
     */
    private static int depthOf(String indent) {
        int tabs = 0;
        int spaces = 0;
        for (int i = 0; i < indent.length(); i++) {
            if (indent.charAt(i) == '\t') {
                tabs++;
            } else {
                spaces++;
            }
        }
        return tabs + spaces / INDENT_UNIT;
    }
}
