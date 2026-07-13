package com.scripty.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.namespace.QName;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;

/**
 * Converts industry-style screenplay DOCX (matching {@link DocxExportServiceImpl} layout)
 * into Fountain text for {@link FountainImportServiceImpl}.
 */
final class DocxToFountainConverter {

    // Twips — must stay in sync with DocxExportServiceImpl
    private static final int TWIPS_PER_INCH = 1440;
    private static final int CHARACTER_INDENT = twips(2.2);
    private static final int DIALOGUE_INDENT = twips(1.0);
    private static final int PARENTHETICAL_INDENT = twips(1.5);
    private static final int PARENTHETICAL_RIGHT = twips(2.5);
    private static final int INDENT_TOLERANCE = 280;

    private static final QName W_T = new QName(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t");
    private static final QName W_BR = new QName(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "br");
    private static final QName W_CR = new QName(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "cr");
    private static final QName W_TAB = new QName(
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tab");

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
        PAGE_BREAK,
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

    private DocxToFountainConverter() {
    }

    /**
     * True when the document uses Scripty/industry screenplay indents or
     * right/center alignment typical of formatted screenplays.
     */
    static boolean looksLikeScreenplayLayout(XWPFDocument document) {
        int screenplaySignals = 0;
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraphText(paragraph);
            if (text.isBlank()) {
                continue;
            }
            ParagraphAlignment alignment = paragraph.getAlignment();
            int left = safeIndent(paragraph.getIndentationLeft());
            if (alignment == ParagraphAlignment.RIGHT
                    || near(left, CHARACTER_INDENT)
                    || near(left, DIALOGUE_INDENT)
                    || near(left, PARENTHETICAL_INDENT)) {
                screenplaySignals++;
                if (screenplaySignals >= 2) {
                    return true;
                }
            }
        }
        return screenplaySignals >= 1;
    }

    static String convert(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = document.getParagraphs();
        if (paragraphs.isEmpty()) {
            return "";
        }

        int bodyStart = findBodyStart(paragraphs);
        StringBuilder out = new StringBuilder();

        if (bodyStart > 0) {
            appendTitlePage(out, paragraphs.subList(0, bodyStart));
        }

        EmitMode mode = EmitMode.START;
        for (int i = bodyStart; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            ElementKind kind = classifyBody(paragraph);
            String text = paragraphText(paragraph);

            switch (kind) {
                case EMPTY -> {
                    ensureBlankLine(out);
                    if (mode == EmitMode.CHARACTER_BLOCK) {
                        mode = EmitMode.OTHER;
                    }
                }
                case PAGE_BREAK -> {
                    ensureBlankLine(out);
                    appendLine(out, "===");
                    mode = EmitMode.OTHER;
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

    /**
     * Body starts after the first page break that follows title-like content,
     * or at 0 when the document has no title page.
     */
    private static int findBodyStart(List<XWPFParagraph> paragraphs) {
        boolean sawTitleContent = false;

        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph paragraph = paragraphs.get(i);
            if (hasPageBreak(paragraph) && paragraphText(paragraph).isBlank()) {
                if (sawTitleContent) {
                    return i + 1;
                }
            }

            ElementKind kind = classifyForTitleScan(paragraph);
            if (kind == ElementKind.EMPTY || kind == ElementKind.PAGE_BREAK) {
                continue;
            }
            if (kind == ElementKind.TITLE_CENTERED || kind == ElementKind.TITLE_CONTACT) {
                sawTitleContent = true;
                continue;
            }
            // First distinctive body element
            return sawTitleContent ? i : 0;
        }

        return sawTitleContent ? paragraphs.size() : 0;
    }

    private static void appendTitlePage(StringBuilder out, List<XWPFParagraph> titleParagraphs) {
        String title = null;
        String credit = null;
        StringBuilder authors = new StringBuilder();
        StringBuilder contact = new StringBuilder();

        for (XWPFParagraph paragraph : titleParagraphs) {
            if (hasPageBreak(paragraph) && paragraphText(paragraph).isBlank()) {
                break;
            }
            ElementKind kind = classifyForTitleScan(paragraph);
            String text = paragraphText(paragraph).trim();
            if (text.isEmpty() || kind == ElementKind.EMPTY || kind == ElementKind.PAGE_BREAK) {
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
                String line = authorLines[i].trim();
                if (!line.isEmpty()) {
                    appendLine(out, line);
                }
            }
        }
        if (contact.length() > 0) {
            String[] contactLines = contact.toString().split("\n", -1);
            appendLine(out, "Contact: " + contactLines[0].trim());
            for (int i = 1; i < contactLines.length; i++) {
                String line = contactLines[i].trim();
                if (!line.isEmpty()) {
                    appendLine(out, line);
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

    private static ElementKind classifyForTitleScan(XWPFParagraph paragraph) {
        if (hasPageBreak(paragraph) && paragraphText(paragraph).isBlank()) {
            return ElementKind.PAGE_BREAK;
        }
        String text = paragraphText(paragraph);
        if (text.isBlank()) {
            return ElementKind.EMPTY;
        }

        ParagraphAlignment alignment = paragraph.getAlignment();
        int left = safeIndent(paragraph.getIndentationLeft());

        if (alignment == ParagraphAlignment.RIGHT
                || near(left, CHARACTER_INDENT)
                || near(left, DIALOGUE_INDENT)
                || near(left, PARENTHETICAL_INDENT)) {
            return ElementKind.ACTION;
        }
        if (alignment == ParagraphAlignment.CENTER) {
            return ElementKind.TITLE_CENTERED;
        }
        String trimmed = text.trim();
        if ((alignment == ParagraphAlignment.LEFT || alignment == ParagraphAlignment.BOTH
                || alignment == null)
                && left <= INDENT_TOLERANCE
                && !SCENE_HEADING.matcher(trimmed).matches()
                && !SHOT.matcher(trimmed).matches()
                && !TRANSITION.matcher(trimmed).matches()) {
            return ElementKind.TITLE_CONTACT;
        }
        return ElementKind.ACTION;
    }

    private static ElementKind classifyBody(XWPFParagraph paragraph) {
        if (hasPageBreak(paragraph) && paragraphText(paragraph).isBlank()) {
            return ElementKind.PAGE_BREAK;
        }

        String text = paragraphText(paragraph);
        if (text.isBlank()) {
            return hasPageBreak(paragraph) ? ElementKind.PAGE_BREAK : ElementKind.EMPTY;
        }

        ParagraphAlignment alignment = paragraph.getAlignment();
        int left = safeIndent(paragraph.getIndentationLeft());
        int right = safeIndent(paragraph.getIndentationRight());
        boolean bold = isMostlyBold(paragraph);
        boolean italic = isMostlyItalic(paragraph);
        String trimmed = text.trim();

        if (alignment == ParagraphAlignment.RIGHT) {
            return ElementKind.TRANSITION;
        }
        if (alignment == ParagraphAlignment.CENTER) {
            return ElementKind.CENTERED;
        }
        if (near(left, CHARACTER_INDENT)) {
            return ElementKind.CHARACTER;
        }
        if (near(left, PARENTHETICAL_INDENT)
                && (near(right, PARENTHETICAL_RIGHT) || trimmed.startsWith("(") || italic)) {
            return ElementKind.PARENTHETICAL;
        }
        if (near(left, DIALOGUE_INDENT)) {
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
        // Scripty DOCX export writes scenes/shots as bold uppercase with no indent
        if (bold && isAllCaps(trimmed) && trimmed.length() <= 120) {
            return ElementKind.SCENE;
        }
        if (italic && !bold) {
            return ElementKind.LYRICS;
        }
        return ElementKind.ACTION;
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

    private static String paragraphText(XWPFParagraph paragraph) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            String fallback = paragraph.getText();
            return fallback != null ? normalizeNewlines(fallback) : "";
        }
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : runs) {
            appendRunText(sb, run);
        }
        return normalizeNewlines(sb.toString());
    }

    private static void appendRunText(StringBuilder sb, XWPFRun run) {
        CTR ctr = run.getCTR();
        if (ctr == null) {
            String t = run.getText(0);
            if (t != null) {
                sb.append(t);
            }
            return;
        }

        int before = sb.length();
        try (XmlCursor cursor = ctr.newCursor()) {
            if (!cursor.toFirstChild()) {
                String t = run.getText(0);
                if (t != null) {
                    sb.append(t);
                }
                return;
            }
            do {
                QName name = cursor.getName();
                if (name == null) {
                    continue;
                }
                if (W_T.equals(name)) {
                    String value = cursor.getTextValue();
                    if (value != null) {
                        sb.append(value);
                    }
                } else if (W_BR.equals(name)) {
                    CTBr br = (CTBr) cursor.getObject();
                    if (br.getType() == null || br.getType() == STBrType.TEXT_WRAPPING) {
                        sb.append('\n');
                    }
                } else if (W_CR.equals(name)) {
                    sb.append('\n');
                } else if (W_TAB.equals(name)) {
                    sb.append('\t');
                }
            } while (cursor.toNextSibling());
        }

        if (sb.length() == before) {
            String t = run.getText(0);
            if (t != null) {
                sb.append(t);
            }
        }
    }

    private static boolean hasPageBreak(XWPFParagraph paragraph) {
        if (paragraph.isPageBreak()) {
            return true;
        }
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null) {
            return false;
        }
        for (XWPFRun run : runs) {
            CTR ctr = run.getCTR();
            if (ctr == null) {
                continue;
            }
            for (CTBr br : ctr.getBrList()) {
                if (br != null && br.getType() == STBrType.PAGE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMostlyBold(XWPFParagraph paragraph) {
        return mostlyStyle(paragraph, true);
    }

    private static boolean isMostlyItalic(XWPFParagraph paragraph) {
        return mostlyStyle(paragraph, false);
    }

    private static boolean mostlyStyle(XWPFParagraph paragraph, boolean wantBold) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs == null || runs.isEmpty()) {
            return false;
        }
        int styledChars = 0;
        int totalChars = 0;
        for (XWPFRun run : runs) {
            String t = run.getText(0);
            if (t == null || t.isBlank()) {
                continue;
            }
            int len = t.trim().length();
            totalChars += len;
            boolean match = wantBold ? run.isBold() : run.isItalic();
            if (match) {
                styledChars += len;
            }
        }
        return totalChars > 0 && styledChars * 2 >= totalChars;
    }

    private static boolean isAllCaps(String text) {
        String letters = text.replaceAll("[^A-Za-z]", "");
        return !letters.isEmpty() && letters.equals(letters.toUpperCase(Locale.ROOT));
    }

    private static int safeIndent(int value) {
        return value < 0 ? 0 : value;
    }

    private static boolean near(int actual, int expected) {
        return Math.abs(actual - expected) <= INDENT_TOLERANCE;
    }

    private static int twips(double inches) {
        return (int) Math.round(inches * TWIPS_PER_INCH);
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void ensureBlankLine(StringBuilder out) {
        if (out.length() == 0) {
            return;
        }
        int len = out.length();
        if (len >= 2 && out.charAt(len - 1) == '\n' && out.charAt(len - 2) == '\n') {
            return;
        }
        if (out.charAt(len - 1) != '\n') {
            out.append('\n');
        }
        out.append('\n');
    }

    private static void appendLine(StringBuilder out, String line) {
        out.append(line).append('\n');
    }

    private static void appendMultiline(StringBuilder out, String text) {
        String[] lines = normalizeNewlines(text).split("\n", -1);
        for (String line : lines) {
            appendLine(out, line.stripTrailing());
        }
    }

    private static void appendAction(StringBuilder out, String text) {
        String[] lines = normalizeNewlines(text).split("\n", -1);
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (needsForcedAction(trimmed.trim())) {
                appendLine(out, "!" + trimmed.trim());
            } else {
                appendLine(out, trimmed);
            }
        }
    }

    private static boolean needsForcedAction(String trimmed) {
        if (trimmed.isEmpty() || trimmed.startsWith("!")) {
            return false;
        }
        if (SCENE_HEADING.matcher(trimmed).matches()
                || TRANSITION.matcher(trimmed).matches()
                || SHOT.matcher(trimmed).matches()) {
            return true;
        }
        if (trimmed.length() > 60) {
            return false;
        }
        String lettersOnly = trimmed.replaceAll("[^A-Za-z]", "");
        return !lettersOnly.isEmpty() && trimmed.equals(trimmed.toUpperCase(Locale.ROOT));
    }
}
