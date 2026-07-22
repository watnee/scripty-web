package com.scripty.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Pulls the words back out of a score.
 *
 * <p>MusicXML is how notation programs — MuseScore, Finale, Sibelius, Dorico —
 * hand a song to anything else, so it is how a lyric that was written against
 * music arrives here. Everything but the words is dropped: Scripty stores a
 * song as ordered lines of text, and there is nowhere to put the notes.
 *
 * <p>The hard part is not the words but where the lines end, because a score
 * has no lines — it has measures and systems. In order of trust:
 *
 * <ol>
 *   <li>{@code <end-line/>} and {@code <end-paragraph/>}, which exist in the
 *       format for exactly this and are what {@link SongMusicXmlWriter} emits;
 *   <li>an explicit system or page break, which in vocal parts a lyric almost
 *       always respects;
 *   <li>failing both, one line per measure — coarse, but a wrong break the
 *       writer can see and join beats a single run-on paragraph.
 * </ol>
 */
final class MusicXmlToLyricsConverter {

    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000;

    /**
     * A score names its DTD, and a parser configured to refuse doctypes — which
     * is the safe default everywhere else here — throws on every real file.
     * Dropping the declaration keeps the refusal and the file.
     */
    private static final Pattern DOCTYPE = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);

    /** How far into the file to look for a root element when the name says nothing. */
    private static final int SNIFF_BYTES = 4096;

    private MusicXmlToLyricsConverter() {
    }

    /** The words of a score, and what it called itself. */
    record Lyrics(String title, String text) {
        boolean isEmpty() {
            return text == null || text.isBlank();
        }
    }

    static boolean looksLikeMusicXml(String lowerName, String contentType) {
        return lowerName.endsWith(".musicxml")
                || lowerName.endsWith(".mxl")
                || contentType.contains("musicxml");
    }

    /**
     * Whether a file that only calls itself {@code .xml} is a score. Notation
     * programs still write plain {@code .xml} by long habit, and the extension
     * alone would send it through as raw markup.
     */
    static boolean looksLikeMusicXmlContent(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        int length = Math.min(content.length, SNIFF_BYTES);
        String head = new String(content, 0, length, StandardCharsets.UTF_8);
        return head.contains("<score-partwise") || head.contains("<score-timewise")
                || head.contains("musicxml.org");
    }

    static String convertPlain(InputStream inputStream) throws IOException {
        return convert(inputStream).text();
    }

    static Lyrics convert(InputStream inputStream) throws IOException {
        byte[] bytes = readAll(inputStream);
        Document document = parse(isZip(bytes) ? unwrapCompressed(bytes) : bytes);
        Element root = document.getDocumentElement();
        if (root == null
                || !("score-partwise".equals(root.getTagName()) || "score-timewise".equals(root.getTagName()))) {
            throw new IOException("Not a MusicXML score");
        }
        List<Event> events = new ArrayList<>();
        collect(root, null, events);
        return new Lyrics(title(root), render(events));
    }

    // --- Reading the file --------------------------------------------------------

    private static boolean isZip(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    /**
     * Unpacks a {@code .mxl}, which is a zip whose {@code META-INF/container.xml}
     * names the score. MuseScore exports this by default, so it is the common
     * case rather than the exotic one.
     */
    private static byte[] unwrapCompressed(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = readZip(bytes);
        byte[] container = entries.get("META-INF/container.xml");
        if (container != null) {
            String path = rootfilePath(container);
            byte[] score = path != null ? entries.get(path) : null;
            if (score != null) {
                return score;
            }
        }
        // A container that lies, or is missing, still leaves the score in the
        // archive under some name; the first plausible one beats giving up.
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT);
            if (name.startsWith("meta-inf/")) {
                continue;
            }
            if (name.endsWith(".musicxml") || name.endsWith(".xml")) {
                return entry.getValue();
            }
        }
        throw new IOException("Not a MusicXML archive: no score inside");
    }

    private static Map<String, byte[]> readZip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entries.size() >= MAX_ENTRIES) {
                    throw new IOException("MusicXML archive has too many entries");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("MusicXML archive is too large to import");
                    }
                    out.write(buffer, 0, read);
                }
                entries.put(entry.getName().replace('\\', '/'), out.toByteArray());
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("Not a MusicXML archive: the file is not a readable archive");
        }
        return entries;
    }

    private static String rootfilePath(byte[] containerXml) {
        try {
            NodeList rootfiles = parse(containerXml).getElementsByTagName("rootfile");
            for (int i = 0; i < rootfiles.getLength(); i++) {
                String path = ((Element) rootfiles.item(i)).getAttribute("full-path");
                if (!path.isBlank()) {
                    return path.replace('\\', '/');
                }
            }
        } catch (IOException e) {
            // Fall back to guessing at the archive's entries.
        }
        return null;
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_TOTAL_BYTES) {
                throw new IOException("MusicXML file is too large to import");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Document parse(byte[] content) throws IOException {
        String xml = DOCTYPE.matcher(new String(content, StandardCharsets.UTF_8)).replaceAll("");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse MusicXML", e);
        }
    }

    // --- Walking the score -------------------------------------------------------

    /**
     * What a note carries for one verse. {@code number} is the verse it belongs
     * to — a hymn's second stanza is a second lyric on the same notes, not a
     * second pass through the score.
     */
    private record Syllable(String number, String text, boolean continues,
                            boolean endLine, boolean endParagraph) {
    }

    /**
     * Every event knows its part, so an instrument's page turns cannot break a
     * line of the vocal. A timewise score nests {@code measure} above
     * {@code part}, leaving its measure boundaries with no part at all; those
     * count for whichever part is being read.
     */
    private sealed interface Event {
        String partId();

        record MeasureStart(String partId) implements Event {
        }

        record Break(String partId) implements Event {
        }

        record Note(String partId, List<Syllable> syllables) implements Event {
        }
    }

    /**
     * Flattens the score into the order it is read in. Partwise and timewise
     * scores nest {@code part} and {@code measure} the opposite way round, but
     * both put a {@code part} above every note, so one walk serves both.
     */
    private static void collect(Element element, String partId, List<Event> events) {
        String tag = element.getTagName();
        switch (tag) {
            case "part" -> {
                String id = element.getAttribute("id");
                partId = id.isBlank() ? partId : id;
            }
            case "measure" -> events.add(new Event.MeasureStart(partId));
            case "print" -> {
                if ("yes".equals(element.getAttribute("new-system"))
                        || "yes".equals(element.getAttribute("new-page"))) {
                    events.add(new Event.Break(partId));
                }
                return;
            }
            case "note" -> {
                events.add(new Event.Note(partId, syllables(element)));
                return;
            }
            default -> {
            }
        }
        for (Element child : children(element)) {
            collect(child, partId, events);
        }
    }

    private static List<Syllable> syllables(Element note) {
        List<Syllable> syllables = new ArrayList<>();
        for (Element lyric : children(note, "lyric")) {
            String number = lyric.getAttribute("number");
            if (number.isBlank()) {
                number = lyric.getAttribute("name");
            }
            if (number.isBlank()) {
                number = "1";
            }
            // A syllable can be split across several <text> runs, joined by an
            // <elision> where two of them share the note.
            StringBuilder text = new StringBuilder();
            String syllabic = null;
            boolean sawText = false;
            for (Element part : children(lyric)) {
                switch (part.getTagName()) {
                    case "syllabic" -> {
                        if (!sawText) {
                            syllabic = textOf(part).trim();
                        }
                    }
                    case "text" -> {
                        text.append(textOf(part));
                        sawText = true;
                    }
                    case "elision" -> text.append(' ');
                    default -> {
                    }
                }
            }
            if (!sawText) {
                // <extend>, <humming> and <laughing> hold a note open under a
                // syllable already sung; there is no new word to record.
                continue;
            }
            boolean continues = "begin".equals(syllabic) || "middle".equals(syllabic);
            syllables.add(new Syllable(number, text.toString(),
                    continues,
                    hasChild(lyric, "end-line"),
                    hasChild(lyric, "end-paragraph")));
        }
        return syllables;
    }

    // --- Turning it into lines ---------------------------------------------------

    /** One verse's words, accumulated across the score. */
    private static final class Verse {
        private final List<String> lines = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private final StringBuilder word = new StringBuilder();

        void syllable(String text, boolean continues) {
            word.append(text);
            if (!continues) {
                endWord();
            }
        }

        private void endWord() {
            if (word.length() == 0) {
                return;
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
            word.setLength(0);
        }

        void endLine() {
            endWord();
            if (line.length() == 0) {
                return;
            }
            lines.add(line.toString());
            line.setLength(0);
        }

        void endStanza() {
            endLine();
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                lines.add("");
            }
        }

        List<String> finish() {
            endLine();
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            return lines;
        }
    }

    private static String render(List<Event> events) {
        String partId = singingPart(events);
        if (partId == null) {
            return "";
        }
        boolean structured = hasBreaks(events, partId);

        Map<String, Verse> verses = new LinkedHashMap<>();
        for (Event event : events) {
            if (!belongsTo(event, partId)) {
                continue;
            }
            if (event instanceof Event.Break) {
                verses.values().forEach(Verse::endLine);
            } else if (event instanceof Event.MeasureStart && !structured) {
                verses.values().forEach(Verse::endLine);
            } else if (event instanceof Event.Note note) {
                for (Syllable syllable : note.syllables()) {
                    Verse verse = verses.computeIfAbsent(syllable.number(), key -> new Verse());
                    verse.syllable(syllable.text(), syllable.continues());
                    if (syllable.endParagraph()) {
                        verse.endStanza();
                    } else if (syllable.endLine()) {
                        verse.endLine();
                    }
                }
            }
        }

        // Verses stack: the second stanza is sung to the same notes, so it can
        // only follow the first, never interleave with it.
        StringBuilder out = new StringBuilder();
        for (Verse verse : ordered(verses)) {
            List<String> lines = verse.finish();
            if (lines.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(String.join("\n", lines));
        }
        return out.toString();
    }

    /**
     * The first part with words in it. A score usually carries instruments too,
     * and they have nothing to say.
     */
    private static String singingPart(List<Event> events) {
        for (Event event : events) {
            if (event instanceof Event.Note note && !note.syllables().isEmpty()) {
                return note.partId() == null ? "" : note.partId();
            }
        }
        return null;
    }

    /** A measure boundary with no part of its own is the whole score's. */
    private static boolean belongsTo(Event event, String target) {
        String partId = event.partId();
        if (partId == null) {
            return event instanceof Event.MeasureStart || target.isEmpty();
        }
        return target.equals(partId);
    }

    private static boolean hasBreaks(List<Event> events, String partId) {
        for (Event event : events) {
            if (!belongsTo(event, partId)) {
                continue;
            }
            if (event instanceof Event.Break) {
                return true;
            }
            if (event instanceof Event.Note note) {
                for (Syllable syllable : note.syllables()) {
                    if (syllable.endLine() || syllable.endParagraph()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Numbered verses in numeric order; anything named keeps the order it appeared in. */
    private static List<Verse> ordered(Map<String, Verse> verses) {
        Set<String> keys = new LinkedHashSet<>(verses.keySet());
        List<String> numeric = new ArrayList<>();
        List<String> named = new ArrayList<>();
        for (String key : keys) {
            (key.matches("\\d+") ? numeric : named).add(key);
        }
        numeric.sort((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
        List<Verse> ordered = new ArrayList<>();
        numeric.forEach(key -> ordered.add(verses.get(key)));
        named.forEach(key -> ordered.add(verses.get(key)));
        return ordered;
    }

    // --- Title -------------------------------------------------------------------

    private static String title(Element root) {
        Element work = firstChild(root, "work");
        if (work != null) {
            String title = textOf(firstChild(work, "work-title"));
            if (!title.isBlank()) {
                return title.trim();
            }
        }
        String movement = textOf(firstChild(root, "movement-title"));
        if (!movement.isBlank()) {
            return movement.trim();
        }
        // Engraved titles live in <credit> instead, which is where a score
        // typed straight into MuseScore usually keeps its name.
        for (Element credit : children(root, "credit")) {
            if (!"title".equals(credit.getAttribute("credit-type"))) {
                continue;
            }
            String words = textOf(firstChild(credit, "credit-words"));
            if (!words.isBlank()) {
                return words.trim();
            }
        }
        return null;
    }

    // --- DOM plumbing ------------------------------------------------------------

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> elements = new ArrayList<>();
        for (Element child : children(parent)) {
            if (tag.equals(child.getTagName())) {
                elements.add(child);
            }
        }
        return elements;
    }

    private static Element firstChild(Element parent, String tag) {
        List<Element> matches = children(parent, tag);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static boolean hasChild(Element parent, String tag) {
        return firstChild(parent, tag) != null;
    }

    private static String textOf(Element element) {
        return element == null || element.getTextContent() == null ? "" : element.getTextContent();
    }
}
