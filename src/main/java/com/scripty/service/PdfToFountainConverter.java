package com.scripty.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Converts screenplay PDFs (matching {@link PdfExportServiceImpl} layout when present)
 * into Fountain text for {@link FountainImportServiceImpl}.
 */
final class PdfToFountainConverter {

    // Absolute X from left page edge — must stay in sync with PdfExportServiceImpl
    // (LEFT_MARGIN 108 + element indentationLeft).
    private static final float ACTION_X = 108f;            // 1.5in
    private static final float DIALOGUE_X = 180f;          // 1.5in + 1in
    private static final float PARENTHETICAL_X = 216f;     // 1.5in + 1.5in
    private static final float CHARACTER_X = 266.4f;       // 1.5in + 2.2in
    private static final float PAGE_WIDTH = 612f;          // US Letter
    private static final float RIGHT_MARGIN = 72f;
    private static final float X_TOLERANCE = 18f;          // ~0.25in
    private static final float Y_LINE_TOLERANCE = 3f;

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

    private enum ElementKind {
        EMPTY,
        TITLE_CENTERED,
        TITLE_CONTACT,
        CHARACTER,
        DIALOGUE,
        PARENTHETICAL,
        TRANSITION,
        CENTERED,
        SCENE,
        SHOT,
        LYRICS,
        ACTION
    }

    private enum EmitMode {
        START,
        CHARACTER_BLOCK,
        OTHER
    }

    private record Glyph(int pageIndex, float x, float endX, float y, String unicode, boolean bold, boolean italic) {
    }

    private record PdfLine(int pageIndex, float x, float y, float endX, String text, boolean bold, boolean italic) {
    }

    private PdfToFountainConverter() {
    }

    static boolean looksLikePdf(String lowerName, String contentType) {
        return lowerName.endsWith(".pdf")
                || "application/pdf".equals(contentType);
    }

    static String convertPlain(InputStream inputStream) throws IOException {
        List<PdfLine> lines = extractLines(inputStream);
        StringBuilder out = new StringBuilder();
        for (PdfLine line : lines) {
            String text = line.text().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(text);
        }
        return out.toString();
    }

    static String convert(InputStream inputStream) throws IOException {
        List<PdfLine> lines = extractLines(inputStream);
        if (lines.isEmpty()) {
            return "";
        }

        if (!looksLikeScreenplayLayout(lines)) {
            return plainFountain(lines);
        }

        int bodyStart = findBodyStart(lines);
        StringBuilder out = new StringBuilder();

        if (bodyStart > 0) {
            appendTitlePage(out, lines.subList(0, bodyStart));
        }

        EmitMode mode = EmitMode.START;
        int lastPage = bodyStart < lines.size() ? lines.get(bodyStart).pageIndex() : 0;
        for (int i = bodyStart; i < lines.size(); i++) {
            PdfLine line = lines.get(i);
            if (line.pageIndex() > lastPage) {
                ensureBlankLine(out);
                appendLine(out, "===");
                mode = EmitMode.OTHER;
                lastPage = line.pageIndex();
            }

            ElementKind kind = classifyBody(line);
            String text = line.text();

            switch (kind) {
                case EMPTY -> {
                    ensureBlankLine(out);
                    if (mode == EmitMode.CHARACTER_BLOCK) {
                        mode = EmitMode.OTHER;
                    }
                }
                case CHARACTER -> {
                    ensureBlankLine(out);
                    appendLine(out, formatCharacterCue(text));
                    mode = EmitMode.CHARACTER_BLOCK;
                }
                case PARENTHETICAL -> {
                    if (mode != EmitMode.CHARACTER_BLOCK) {
                        ensureBlankLine(out);
                    }
                    appendLine(out, formatParenthetical(text));
                    mode = EmitMode.CHARACTER_BLOCK;
                }
                case DIALOGUE -> {
                    if (mode != EmitMode.CHARACTER_BLOCK) {
                        ensureBlankLine(out);
                    }
                    appendMultiline(out, text);
                    mode = EmitMode.CHARACTER_BLOCK;
                }
                case TRANSITION -> {
                    ensureBlankLine(out);
                    appendLine(out, formatTransition(text));
                    mode = EmitMode.OTHER;
                }
                case CENTERED -> {
                    ensureBlankLine(out);
                    appendLine(out, ">" + text.trim() + "<");
                    mode = EmitMode.OTHER;
                }
                case SCENE -> {
                    ensureBlankLine(out);
                    appendLine(out, formatScene(text));
                    mode = EmitMode.OTHER;
                }
                case SHOT -> {
                    ensureBlankLine(out);
                    appendLine(out, text.trim());
                    mode = EmitMode.OTHER;
                }
                case LYRICS -> {
                    ensureBlankLine(out);
                    appendLine(out, "~" + text.trim());
                    mode = EmitMode.OTHER;
                }
                default -> {
                    ensureBlankLine(out);
                    appendAction(out, text);
                    mode = EmitMode.OTHER;
                }
            }
        }

        String result = out.toString().stripTrailing();
        return result.isEmpty() ? "" : result + "\n";
    }

    static boolean looksLikeScreenplayLayout(List<PdfLine> lines) {
        int signals = 0;
        for (PdfLine line : lines) {
            if (line.text().isBlank()) {
                continue;
            }
            if (near(line.x(), CHARACTER_X)
                    || near(line.x(), DIALOGUE_X)
                    || near(line.x(), PARENTHETICAL_X)
                    || isRightAligned(line)) {
                signals++;
                if (signals >= 2) {
                    return true;
                }
            }
        }
        return signals >= 1;
    }

    private static String plainFountain(List<PdfLine> lines) {
        StringBuilder out = new StringBuilder();
        for (PdfLine line : lines) {
            String text = line.text().stripTrailing();
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(text);
        }
        String result = out.toString().stripTrailing();
        return result.isEmpty() ? "" : result + "\n";
    }

    private static List<PdfLine> extractLines(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            GlyphCollector collector = new GlyphCollector();
            collector.setSortByPosition(true);
            collector.getText(document);
            return groupIntoLines(collector.glyphs());
        }
    }

    private static List<PdfLine> groupIntoLines(List<Glyph> glyphs) {
        List<PdfLine> lines = new ArrayList<>();
        if (glyphs.isEmpty()) {
            return lines;
        }

        List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator
                .comparingInt(Glyph::pageIndex)
                .thenComparing(Glyph::y)
                .thenComparing(Glyph::x));

        List<Glyph> current = new ArrayList<>();
        int page = sorted.get(0).pageIndex();
        float y = sorted.get(0).y();

        for (Glyph glyph : sorted) {
            if (glyph.pageIndex() != page || Math.abs(glyph.y() - y) > Y_LINE_TOLERANCE) {
                PdfLine line = toLine(current);
                if (line != null) {
                    lines.add(line);
                }
                current.clear();
                page = glyph.pageIndex();
                y = glyph.y();
            }
            current.add(glyph);
        }
        PdfLine last = toLine(current);
        if (last != null) {
            lines.add(last);
        }
        return lines;
    }

    private static PdfLine toLine(List<Glyph> glyphs) {
        if (glyphs.isEmpty()) {
            return null;
        }
        glyphs.sort(Comparator.comparing(Glyph::x));

        StringBuilder text = new StringBuilder();
        float minX = Float.MAX_VALUE;
        float maxEndX = Float.MIN_VALUE;
        float y = glyphs.get(0).y();
        int page = glyphs.get(0).pageIndex();
        int boldCount = 0;
        int italicCount = 0;
        int letterCount = 0;

        Glyph prev = null;
        for (Glyph glyph : glyphs) {
            String unicode = glyph.unicode();
            if (unicode == null || unicode.isEmpty()) {
                continue;
            }
            if (prev != null && !unicode.equals(" ") && text.length() > 0
                    && text.charAt(text.length() - 1) != ' ') {
                float gap = glyph.x() - prev.endX();
                float spaceWidth = Math.max(prev.endX() - prev.x(), 1f);
                // Insert a space only when the gap is clearly a word gap (~half a glyph+)
                if (gap > spaceWidth * 0.45f) {
                    text.append(' ');
                }
            }
            text.append(unicode);
            minX = Math.min(minX, glyph.x());
            maxEndX = Math.max(maxEndX, glyph.endX());
            boolean isLetter = unicode.chars().anyMatch(Character::isLetter);
            if (isLetter) {
                letterCount++;
                if (glyph.bold()) {
                    boldCount++;
                }
                if (glyph.italic()) {
                    italicCount++;
                }
            }
            if (!unicode.equals(" ")) {
                prev = glyph;
            }
        }

        String lineText = text.toString().replace('\u00A0', ' ').stripTrailing();
        if (lineText.isBlank()) {
            return null;
        }
        boolean bold = letterCount > 0 && boldCount * 2 >= letterCount;
        boolean italic = letterCount > 0 && italicCount * 2 >= letterCount;
        return new PdfLine(page, minX, y, maxEndX, lineText, bold, italic);
    }

    /**
     * Body starts at the first distinctive screenplay element. Title-page content
     * (centered title/credit/author, left contact) may span multiple PDF pages when
     * vertical spacers force a page break before contact info.
     */
    private static int findBodyStart(List<PdfLine> lines) {
        boolean sawTitleContent = false;

        for (int i = 0; i < lines.size(); i++) {
            PdfLine line = lines.get(i);
            ElementKind kind = classifyForTitleScan(line);
            if (kind == ElementKind.EMPTY) {
                continue;
            }
            if (kind == ElementKind.TITLE_CENTERED || kind == ElementKind.TITLE_CONTACT) {
                sawTitleContent = true;
                continue;
            }
            // First distinctive body element
            return sawTitleContent ? i : 0;
        }

        return sawTitleContent ? lines.size() : 0;
    }

    private static void appendTitlePage(StringBuilder out, List<PdfLine> titleLines) {
        String title = null;
        String credit = null;
        StringBuilder authors = new StringBuilder();
        StringBuilder contact = new StringBuilder();

        for (PdfLine line : titleLines) {
            ElementKind kind = classifyForTitleScan(line);
            String text = line.text().trim();
            if (text.isEmpty() || kind == ElementKind.EMPTY) {
                continue;
            }

            if (kind == ElementKind.TITLE_CONTACT) {
                if (contact.length() > 0) {
                    contact.append('\n');
                }
                contact.append(text);
                continue;
            }

            if (CREDIT_LINE.matcher(text).matches()) {
                credit = text;
                continue;
            }
            if (title == null) {
                title = text;
                continue;
            }
            if (authors.length() > 0) {
                authors.append('\n');
            }
            authors.append(text);
        }

        if (title != null && !title.isBlank()) {
            appendLine(out, "Title: " + title);
        }
        if (credit != null && !credit.isBlank()) {
            appendLine(out, "Credit: " + credit);
        }
        if (authors.length() > 0) {
            String[] authorLines = authors.toString().split("\n", -1);
            appendLine(out, "Author: " + authorLines[0].trim());
            for (int i = 1; i < authorLines.length; i++) {
                String authorLine = authorLines[i].trim();
                if (!authorLine.isEmpty()) {
                    appendLine(out, authorLine);
                }
            }
        }
        if (contact.length() > 0) {
            String[] contactLines = contact.toString().split("\n", -1);
            appendLine(out, "Contact: " + contactLines[0].trim());
            for (int i = 1; i < contactLines.length; i++) {
                String contactLine = contactLines[i].trim();
                if (!contactLine.isEmpty()) {
                    appendLine(out, contactLine);
                }
            }
        }

        if (out.length() > 0) {
            if (out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append('\n');
        }
    }

    private static ElementKind classifyForTitleScan(PdfLine line) {
        String text = line.text();
        if (text.isBlank()) {
            return ElementKind.EMPTY;
        }
        if (near(line.x(), CHARACTER_X)
                || near(line.x(), DIALOGUE_X)
                || near(line.x(), PARENTHETICAL_X)
                || isRightAligned(line)) {
            return ElementKind.ACTION;
        }
        String trimmed = text.trim();
        if (SCENE_HEADING.matcher(trimmed).matches()
                || SHOT.matcher(trimmed).matches()
                || TRANSITION.matcher(trimmed).matches()
                || (line.bold() && isAllCaps(trimmed) && trimmed.length() > 15
                && (trimmed.contains(" - ") || trimmed.contains(" – ")))) {
            return ElementKind.SCENE;
        }
        if (isCentered(line)) {
            return ElementKind.TITLE_CENTERED;
        }
        if (near(line.x(), ACTION_X)) {
            return ElementKind.TITLE_CONTACT;
        }
        return ElementKind.ACTION;
    }

    private static ElementKind classifyBody(PdfLine line) {
        String text = line.text();
        if (text.isBlank()) {
            return ElementKind.EMPTY;
        }

        String trimmed = text.trim();

        if (isRightAligned(line)) {
            return ElementKind.TRANSITION;
        }
        if (isCentered(line)) {
            return ElementKind.CENTERED;
        }
        if (near(line.x(), CHARACTER_X)) {
            return ElementKind.CHARACTER;
        }
        if (near(line.x(), PARENTHETICAL_X)
                && (trimmed.startsWith("(") || line.italic())) {
            return ElementKind.PARENTHETICAL;
        }
        if (near(line.x(), DIALOGUE_X)) {
            return ElementKind.DIALOGUE;
        }
        if (SCENE_HEADING.matcher(trimmed).matches()) {
            return ElementKind.SCENE;
        }
        if (SHOT.matcher(trimmed).matches()) {
            return ElementKind.SHOT;
        }
        if (TRANSITION.matcher(trimmed).matches()) {
            return ElementKind.TRANSITION;
        }
        if (line.bold() && isAllCaps(trimmed) && trimmed.length() <= 120) {
            return ElementKind.SCENE;
        }
        if (line.italic() && !line.bold()) {
            return ElementKind.LYRICS;
        }
        return ElementKind.ACTION;
    }

    private static boolean isCentered(PdfLine line) {
        float pageCenter = PAGE_WIDTH / 2f;
        float lineCenter = (line.x() + line.endX()) / 2f;
        return Math.abs(lineCenter - pageCenter) <= X_TOLERANCE * 1.5f
                && !near(line.x(), ACTION_X)
                && !near(line.x(), DIALOGUE_X)
                && !near(line.x(), CHARACTER_X)
                && !near(line.x(), PARENTHETICAL_X);
    }

    private static boolean isRightAligned(PdfLine line) {
        float rightEdge = PAGE_WIDTH - RIGHT_MARGIN;
        return Math.abs(line.endX() - rightEdge) <= X_TOLERANCE * 1.5f
                && line.x() > ACTION_X + X_TOLERANCE;
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) <= X_TOLERANCE;
    }

    private static boolean isAllCaps(String text) {
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    private static String formatCharacterCue(String text) {
        String name = text.trim().replaceAll("\\s+", " ");
        if (name.startsWith("@")) {
            return name;
        }
        return "@" + name.toUpperCase(Locale.ROOT);
    }

    private static String formatParenthetical(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed;
        }
        return "(" + trimmed + ")";
    }

    private static String formatTransition(String text) {
        String trimmed = text.trim();
        if (TRANSITION.matcher(trimmed).matches() || trimmed.startsWith(">")) {
            return trimmed;
        }
        return ">" + trimmed;
    }

    private static String formatScene(String text) {
        String trimmed = text.trim();
        if (SCENE_HEADING.matcher(trimmed).matches()) {
            return trimmed;
        }
        if (trimmed.startsWith(".") && !trimmed.startsWith("..")) {
            return trimmed;
        }
        return "." + trimmed;
    }

    private static void appendAction(StringBuilder out, String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) {
            return;
        }
        if (looksLikeCharacterCue(trimmed) && !trimmed.startsWith("!")) {
            appendLine(out, "!" + trimmed);
        } else {
            appendMultiline(out, trimmed);
        }
    }

    private static boolean looksLikeCharacterCue(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 40) {
            return false;
        }
        return isAllCaps(trimmed) && !SCENE_HEADING.matcher(trimmed).matches()
                && !TRANSITION.matcher(trimmed).matches()
                && !SHOT.matcher(trimmed).matches();
    }

    private static void ensureBlankLine(StringBuilder out) {
        if (out.isEmpty()) {
            return;
        }
        if (out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        if (out.length() < 2 || out.charAt(out.length() - 2) != '\n') {
            out.append('\n');
        }
    }

    private static void appendLine(StringBuilder out, String line) {
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        out.append(line).append('\n');
    }

    private static void appendMultiline(StringBuilder out, String text) {
        String[] parts = text.split("\n", -1);
        for (String part : parts) {
            appendLine(out, part);
        }
    }

    /**
     * Collects every glyph with page/position metadata; line grouping happens afterward.
     */
    private static final class GlyphCollector extends PDFTextStripper {
        private final List<Glyph> glyphs = new ArrayList<>();
        private int pageIndex = -1;

        GlyphCollector() throws IOException {
            super();
        }

        List<Glyph> glyphs() {
            return glyphs;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            pageIndex++;
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (textPositions == null || textPositions.isEmpty()) {
                return;
            }
            for (TextPosition position : textPositions) {
                String unicode = position.getUnicode();
                if (unicode == null || unicode.isEmpty()) {
                    continue;
                }
                // Skip spacer-only glyphs used for vertical padding
                if (unicode.isBlank()) {
                    continue;
                }
                String fontName = position.getFont() != null && position.getFont().getName() != null
                        ? position.getFont().getName().toLowerCase(Locale.ROOT)
                        : "";
                boolean bold = fontName.contains("bold");
                boolean italic = fontName.contains("italic") || fontName.contains("oblique");
                glyphs.add(new Glyph(
                        pageIndex,
                        position.getXDirAdj(),
                        position.getXDirAdj() + position.getWidthDirAdj(),
                        position.getYDirAdj(),
                        unicode,
                        bold,
                        italic));
            }
        }
    }
}
