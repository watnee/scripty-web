package com.scripty.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Converts an EPUB into Fountain text for {@link FountainImportServiceImpl}.
 *
 * <p>Books produced by {@link EpubExportServiceImpl} carry a block-type class on every
 * paragraph, which is mapped straight back to the matching Fountain construct. EPUBs from
 * other tools have no such classes, so their paragraphs come through as plain lines and the
 * Fountain importer's own heuristics decide what each one is.
 */
final class EpubToFountainConverter {

    /** Guards against decompression bombs from untrusted uploads. */
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000;

    private static final Pattern SCENE_HEADING = Pattern.compile(
            "^(?:INT\\.?|EXT\\.?|EST\\.?|INT\\.?/EXT\\.?|I/E\\.?)\\s+.+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSITION = Pattern.compile("^[A-Z][A-Z0-9 ]+ TO:$");
    private static final Pattern SHOT = Pattern.compile(
            "^(?:ANGLE ON|ANOTHER ANGLE|CLOSE ON|CLOSE UP|CLOSEUP|C\\.U\\.?|CU|POV|INSERT|"
                    + "BACK TO SCENE|BACK TO|TIGHT ON|WIDER(?: SHOT)?|TRACKING|CRANE|"
                    + "AERIAL|ESTABLISHING|FAVOR ON|REVERSE ANGLE)\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_LINE = Pattern.compile(
            "^(?:written(?:\\s+and\\s+directed)?\\s+by|story\\s+by|screenplay\\s+by|by)\\.?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCTYPE = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);

    private EpubToFountainConverter() {
    }

    static boolean looksLikeEpub(String lowerName, String contentType) {
        return lowerName.endsWith(".epub")
                || "application/epub+zip".equals(contentType);
    }

    /** Title page fields recovered from a Scripty-generated title page document. */
    private static final class TitlePage {
        private String title;
        private final List<String> writers = new ArrayList<>();
        private String version;
        private final List<String> contact = new ArrayList<>();

        private boolean isEmpty() {
            return (title == null || title.isBlank())
                    && writers.isEmpty() && version == null && contact.isEmpty();
        }
    }

    static String convert(InputStream inputStream) throws IOException {
        List<Document> documents = readSpineDocuments(inputStream);

        StringBuilder out = new StringBuilder();
        TitlePage titlePage = new TitlePage();
        StringBuilder body = new StringBuilder();

        for (Document document : documents) {
            Element root = document.getDocumentElement();
            if (root == null) {
                continue;
            }
            if (titlePage.isEmpty() && isTitlePage(root)) {
                readTitlePage(root, titlePage);
                continue;
            }
            appendBody(body, root);
        }

        appendTitlePage(out, titlePage);
        out.append(body);
        String exported = out.toString().stripTrailing();
        return exported.isEmpty() ? "" : exported + "\n";
    }

    static String convertPlain(InputStream inputStream) throws IOException {
        List<Document> documents = readSpineDocuments(inputStream);
        StringBuilder out = new StringBuilder();
        for (Document document : documents) {
            Element root = document.getDocumentElement();
            if (root == null) {
                continue;
            }
            for (Element element : textElements(root)) {
                String text = elementText(element).trim();
                if (text.isEmpty()) {
                    continue;
                }
                // Paragraphs are separated by a blank line, not just a newline: that is how a
                // stanza break in an exported songbook — or a paragraph break in any book —
                // comes back as something the writer recognizes.
                if (out.length() > 0) {
                    out.append("\n\n");
                }
                out.append(text);
            }
        }
        return out.toString();
    }

    // --- EPUB container reading -------------------------------------------------

    private static List<Document> readSpineDocuments(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = readZip(inputStream);

        byte[] container = entries.get("META-INF/container.xml");
        if (container == null) {
            throw new IOException("Not an EPUB: missing META-INF/container.xml");
        }
        String opfPath = rootfilePath(container);
        byte[] opf = opfPath != null ? entries.get(opfPath) : null;
        if (opf == null) {
            throw new IOException("Not a readable EPUB: missing package document");
        }

        List<Document> documents = new ArrayList<>();
        for (String href : spineHrefs(opf, opfPath)) {
            byte[] content = entries.get(href);
            if (content == null) {
                continue;
            }
            try {
                documents.add(parseXhtml(content));
            } catch (IOException e) {
                // A single unreadable chapter shouldn't sink the whole import.
            }
        }
        return documents;
    }

    private static Map<String, byte[]> readZip(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entries.size() >= MAX_ENTRIES) {
                    throw new IOException("EPUB has too many entries");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_TOTAL_BYTES) {
                        throw new IOException("EPUB contents are too large to import");
                    }
                    out.write(buffer, 0, read);
                }
                entries.put(entry.getName(), out.toByteArray());
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("Not an EPUB: the file is not a readable archive");
        }
        return entries;
    }

    private static String rootfilePath(byte[] containerXml) throws IOException {
        Document document = parseXml(containerXml);
        NodeList rootfiles = document.getElementsByTagName("rootfile");
        for (int i = 0; i < rootfiles.getLength(); i++) {
            Element rootfile = (Element) rootfiles.item(i);
            String path = rootfile.getAttribute("full-path");
            if (!path.isBlank()) {
                return normalizePath(path);
            }
        }
        return null;
    }

    /** Resolves the spine into archive-relative document paths, in reading order. */
    private static List<String> spineHrefs(byte[] opf, String opfPath) throws IOException {
        Document document = parseXml(opf);
        String base = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

        Map<String, String> manifest = new LinkedHashMap<>();
        NodeList items = document.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            String mediaType = item.getAttribute("media-type");
            String properties = item.getAttribute("properties");
            // The navigation document isn't part of the readable script.
            if (properties != null && properties.contains("nav")) {
                continue;
            }
            if (!id.isBlank() && !href.isBlank() && "application/xhtml+xml".equals(mediaType)) {
                manifest.put(id, normalizePath(base + href));
            }
        }

        List<String> hrefs = new ArrayList<>();
        NodeList itemrefs = document.getElementsByTagName("itemref");
        for (int i = 0; i < itemrefs.getLength(); i++) {
            Element itemref = (Element) itemrefs.item(i);
            String href = manifest.get(itemref.getAttribute("idref"));
            if (href != null) {
                hrefs.add(href);
            }
        }
        // Some books ship a spine we can't resolve; fall back to manifest order.
        return hrefs.isEmpty() ? new ArrayList<>(manifest.values()) : hrefs;
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    // --- XML parsing ------------------------------------------------------------

    private static Document parseXml(byte[] content) throws IOException {
        try {
            return secureFactory().newDocumentBuilder().parse(new ByteArrayInputStream(content));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse EPUB document", e);
        }
    }

    /**
     * XHTML chapters routinely carry a {@code <!DOCTYPE html>} and named HTML entities, neither of
     * which a DTD-less parser accepts, so both are rewritten before parsing.
     */
    private static Document parseXhtml(byte[] content) throws IOException {
        String xml = new String(content, StandardCharsets.UTF_8);
        xml = DOCTYPE.matcher(xml).replaceAll("");
        xml = replaceNamedEntities(xml);
        try {
            return secureFactory().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse EPUB chapter", e);
        }
    }

    private static String replaceNamedEntities(String xml) {
        return xml.replace("&nbsp;", "&#160;")
                .replace("&mdash;", "&#8212;")
                .replace("&ndash;", "&#8211;")
                .replace("&hellip;", "&#8230;")
                .replace("&ldquo;", "&#8220;")
                .replace("&rdquo;", "&#8221;")
                .replace("&lsquo;", "&#8216;")
                .replace("&rsquo;", "&#8217;")
                .replace("&copy;", "&#169;")
                .replace("&trade;", "&#8482;");
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    // --- Title page -------------------------------------------------------------

    private static boolean isTitlePage(Element root) {
        for (Element element : descendants(root)) {
            if (classes(element).contains("title-page")
                    || "titlepage".equals(element.getAttribute("epub:type"))) {
                return true;
            }
        }
        return false;
    }

    private static void readTitlePage(Element root, TitlePage titlePage) {
        for (Element element : descendants(root)) {
            List<String> classes = classes(element);
            String text = elementText(element).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (classes.contains("screenplay-title")) {
                titlePage.title = text;
            } else if (classes.contains("writers")) {
                titlePage.writers.add(text);
            } else if (classes.contains("version")) {
                titlePage.version = text;
            } else if (classes.contains("contact")) {
                titlePage.contact.add(text);
            }
        }
    }

    private static void appendTitlePage(StringBuilder out, TitlePage titlePage) {
        if (titlePage.isEmpty()) {
            return;
        }
        if (titlePage.title != null && !titlePage.title.isBlank()) {
            appendLine(out, "Title: " + titlePage.title.trim());
        }
        appendWriters(out, titlePage.writers);
        if (titlePage.version != null && !titlePage.version.isBlank()) {
            appendLine(out, "Draft date: " + titlePage.version.trim());
        }
        if (!titlePage.contact.isEmpty()) {
            appendLine(out, "Contact: " + titlePage.contact.get(0));
            for (int i = 1; i < titlePage.contact.size(); i++) {
                appendLine(out, titlePage.contact.get(i));
            }
        }
        // Fountain requires a blank line between the title page and the body.
        out.append('\n');
    }

    /** Mirrors the exporter: a leading credit line ("Written by") splits off as {@code Credit:}. */
    private static void appendWriters(StringBuilder out, List<String> writers) {
        if (writers.isEmpty()) {
            return;
        }
        int authorStart = 0;
        if (CREDIT_LINE.matcher(writers.get(0)).matches()) {
            appendLine(out, "Credit: " + writers.get(0));
            authorStart = 1;
        }
        if (authorStart >= writers.size()) {
            return;
        }
        appendLine(out, "Author: " + writers.get(authorStart));
        for (int i = authorStart + 1; i < writers.size(); i++) {
            appendLine(out, writers.get(i));
        }
    }

    // --- Body -------------------------------------------------------------------

    private static void appendBody(StringBuilder out, Element root) {
        for (Element element : textElements(root)) {
            String tag = element.getTagName().toLowerCase(Locale.ROOT);
            if ("hr".equals(tag)) {
                if (classes(element).contains("page-break")) {
                    appendBlankLine(out);
                    appendLine(out, "===");
                }
                continue;
            }

            String content = elementText(element);
            if (content.trim().isEmpty()) {
                continue;
            }
            List<String> classes = classes(element);
            String type = blockType(classes);
            if (type == null) {
                // Not one of ours: headings read as sections, everything else as plain lines.
                appendBlankLine(out);
                if (tag.matches("h[1-6]")) {
                    appendLine(out, "#" + content.trim());
                } else {
                    appendLine(out, content.trim());
                }
                continue;
            }
            appendTypedBlock(out, type, content, classes);
        }
    }

    private static void appendTypedBlock(StringBuilder out, String type, String content, List<String> classes) {
        String trimmed = content.trim();
        switch (type) {
            case "scene" -> {
                appendBlankLine(out);
                String body = emphasize(trimmed, classes);
                appendLine(out, SCENE_HEADING.matcher(trimmed).matches() ? body : "." + body);
            }
            case "action" -> {
                appendBlankLine(out);
                String body = emphasize(content.stripTrailing(), classes);
                appendLine(out, needsForcedAction(trimmed) ? "!" + body : body);
            }
            case "character", "dual-dialogue" -> {
                appendBlankLine(out);
                String name = trimmed.toUpperCase(Locale.ROOT);
                String cue = needsForcedCharacter(name) ? "@" + name : name;
                if ("dual-dialogue".equals(type)) {
                    cue = cue + " ^";
                }
                appendLine(out, emphasize(cue, classes));
            }
            case "dialogue" -> {
                for (String line : content.stripTrailing().split("\n", -1)) {
                    appendLine(out, emphasize(line, classes));
                }
            }
            case "parenthetical" -> {
                String paren = trimmed.startsWith("(") && trimmed.endsWith(")") ? trimmed : "(" + trimmed + ")";
                appendLine(out, emphasize(paren, classes));
            }
            case "transition" -> {
                appendBlankLine(out);
                String body = emphasize(trimmed, classes);
                appendLine(out, TRANSITION.matcher(trimmed).matches() ? body : ">" + body);
            }
            case "shot" -> {
                appendBlankLine(out);
                appendLine(out, emphasize(trimmed.toUpperCase(Locale.ROOT), classes));
            }
            case "lyrics" -> {
                appendBlankLine(out);
                appendLine(out, "~" + emphasize(trimmed, classes));
            }
            case "centered" -> {
                appendBlankLine(out);
                appendLine(out, ">" + emphasize(trimmed, classes) + "<");
            }
            case "section" -> {
                appendBlankLine(out);
                appendLine(out, "#" + emphasize(trimmed, classes));
            }
            case "synopsis" -> {
                appendBlankLine(out);
                appendLine(out, "=" + emphasize(trimmed, classes));
            }
            case "note" -> {
                appendBlankLine(out);
                appendLine(out, "[[" + trimmed + "]]");
            }
            default -> {
                appendBlankLine(out);
                appendLine(out, emphasize(trimmed, classes));
            }
        }
    }

    private static String blockType(List<String> classes) {
        for (String candidate : classes) {
            switch (candidate) {
                case "scene", "action", "text", "character", "dialogue", "dual-dialogue",
                     "parenthetical", "transition", "shot", "lyrics", "centered",
                     "section", "synopsis", "note" -> {
                    return candidate;
                }
                default -> {
                    // Not a block-type class; keep looking.
                }
            }
        }
        return null;
    }

    /** Re-applies whole-block emphasis as Fountain markers, mirroring the Fountain exporter. */
    private static String emphasize(String text, List<String> classes) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        boolean bold = classes.contains("bold");
        boolean italic = classes.contains("italic");
        boolean underline = classes.contains("underline");
        if (!bold && !italic && !underline) {
            return text;
        }
        String wrapped = text;
        if (bold && italic) {
            wrapped = "***" + wrapped + "***";
        } else if (bold) {
            wrapped = "**" + wrapped + "**";
        } else if (italic) {
            wrapped = "*" + wrapped + "*";
        }
        if (underline) {
            wrapped = "_" + wrapped + "_";
        }
        return wrapped;
    }

    private static boolean needsForcedAction(String trimmed) {
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("!") || trimmed.startsWith("@") || trimmed.startsWith("~")
                || trimmed.startsWith("#") || trimmed.startsWith("=") || trimmed.startsWith("[[")
                || trimmed.startsWith(">") || (trimmed.startsWith(".") && !trimmed.startsWith(".."))) {
            return false;
        }
        if (SCENE_HEADING.matcher(trimmed).matches()
                || TRANSITION.matcher(trimmed).matches()
                || SHOT.matcher(trimmed).matches()) {
            return true;
        }
        return looksLikeCharacterCue(trimmed);
    }

    private static boolean needsForcedCharacter(String upperName) {
        if (upperName.isEmpty()) {
            return false;
        }
        if (SCENE_HEADING.matcher(upperName).matches()
                || TRANSITION.matcher(upperName).matches()
                || SHOT.matcher(upperName).matches()) {
            return true;
        }
        return upperName.length() > 60 || !looksLikeCharacterCue(upperName);
    }

    private static boolean looksLikeCharacterCue(String line) {
        if (line.isEmpty() || line.length() > 60) {
            return false;
        }
        String withoutModifiers = line.replaceAll("\\^(\\*)?", "").trim();
        if (withoutModifiers.startsWith("@")) {
            withoutModifiers = withoutModifiers.substring(1).trim();
        }
        if (withoutModifiers.isEmpty()) {
            return false;
        }
        String lettersOnly = withoutModifiers.replaceAll("[^A-Za-z]", "");
        if (lettersOnly.isEmpty()) {
            return false;
        }
        return withoutModifiers.equals(withoutModifiers.toUpperCase(Locale.ROOT));
    }

    // --- DOM helpers ------------------------------------------------------------

    /** Leaf-ish content elements in document order: paragraphs, headings, and page breaks. */
    private static List<Element> textElements(Element root) {
        List<Element> elements = new ArrayList<>();
        for (Element element : descendants(root)) {
            String tag = element.getTagName().toLowerCase(Locale.ROOT);
            if (tag.equals("p") || tag.equals("hr") || tag.matches("h[1-6]")) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static List<Element> descendants(Element root) {
        List<Element> elements = new ArrayList<>();
        collectDescendants(root, elements);
        return elements;
    }

    private static void collectDescendants(Element parent, List<Element> out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element element) {
                out.add(element);
                collectDescendants(element, out);
            }
        }
    }

    private static List<String> classes(Element element) {
        String value = element.getAttribute("class");
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim().toLowerCase(Locale.ROOT).split("\\s+"));
    }

    /** Element text with {@code <br/>} restored as newlines. */
    private static String elementText(Element element) {
        StringBuilder text = new StringBuilder();
        collectText(element, text);
        return text.toString();
    }

    private static void collectText(Node parent, StringBuilder out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value = child.getNodeValue();
                if (value != null) {
                    out.append(value);
                }
            } else if (child instanceof Element element) {
                if ("br".equalsIgnoreCase(element.getTagName())) {
                    out.append('\n');
                } else {
                    collectText(element, out);
                }
            }
        }
    }

    private static void appendBlankLine(StringBuilder sb) {
        if (sb.isEmpty()) {
            return;
        }
        if (sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append('\n');
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        sb.append(line).append('\n');
    }
}
