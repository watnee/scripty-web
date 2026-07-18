package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

/**
 * Guards the property the export formats kept losing: that everything which
 * carries real page geometry agrees with {@link ScreenplayLayout}, and therefore
 * with the on-screen page view.
 *
 * <p>These assertions are deliberately written against ScreenplayLayout rather
 * than against literal numbers, so changing a measurement in one place stays
 * legal while changing it in only <em>one exporter</em> fails here.
 *
 * <p>Fountain and EPUB are intentionally absent: Fountain carries no geometry at
 * all (blank lines are its only separator, and its speech-group handling is
 * correct by construction), and EPUB is reflowable, so the reading system owns
 * page size and font metrics. See {@code EpubExportServiceImpl.styleCss}.
 */
class ScreenplayLayoutParityTest {

    // --- The layout itself --------------------------------------------------

    @Test
    void textColumnAndElementsFitTheUsWiderPage() {
        assertEquals(6.0, ScreenplayLayout.TEXT_WIDTH_IN, 1e-9,
                "8.5in page less a 1.5in gutter and 1in right margin is a 6in column");

        assertTrue(ScreenplayLayout.DIALOGUE_INDENT_IN + ScreenplayLayout.DIALOGUE_WIDTH_IN
                        <= ScreenplayLayout.TEXT_WIDTH_IN,
                "dialogue must fit inside the text column");
        assertTrue(ScreenplayLayout.PARENTHETICAL_INDENT_IN + ScreenplayLayout.PARENTHETICAL_WIDTH_IN
                        <= ScreenplayLayout.TEXT_WIDTH_IN,
                "parentheticals must fit inside the text column");
        assertTrue(ScreenplayLayout.CHARACTER_INDENT_IN < ScreenplayLayout.TEXT_WIDTH_IN,
                "the character cue must start inside the text column");

        // A parenthetical sits between the dialogue margin and the character cue.
        assertTrue(ScreenplayLayout.DIALOGUE_INDENT_IN < ScreenplayLayout.PARENTHETICAL_INDENT_IN,
                "parentheticals are indented further than dialogue");
        assertTrue(ScreenplayLayout.PARENTHETICAL_INDENT_IN < ScreenplayLayout.CHARACTER_INDENT_IN,
                "character cues are indented further than parentheticals");
    }

    @Test
    void sceneBreakIsLargerThanTheElementGapAndSpeechGroupsAreTight() {
        assertTrue(ScreenplayLayout.SCENE_SPACING_PT > ScreenplayLayout.ELEMENT_SPACING_PT,
                "a scene heading gets a fuller break than an ordinary element");
        assertEquals(0.0, ScreenplayLayout.SPEECH_GROUP_SPACING_PT, 1e-9,
                "dialogue and parentheticals hug the character cue above them");
    }

    @Test
    void unitConversionsRoundTrip() {
        assertEquals(108f, ScreenplayLayout.pt(1.5), 1e-4);
        assertEquals(2160, ScreenplayLayout.twips(1.5));
        // Spacing is authored in points but DOCX wants twips: 12pt = 240 twips.
        assertEquals(240, ScreenplayLayout.twipsFromPoints(12.0));
        assertEquals(3.7, ScreenplayLayout.fromPageEdge(ScreenplayLayout.CHARACTER_INDENT_IN), 1e-9);
        assertEquals(7.5, ScreenplayLayout.rightTextEdgeFromPageEdge(), 1e-9);
    }

    // --- PDF ----------------------------------------------------------------

    @Test
    void pdfPlacesEachElementAtItsLayoutIndent() throws Exception {
        byte[] pdf = PdfExportServiceImpl.toPdf(null, sampleBlocks());
        String content = pdfTextOperators(pdf);

        // OpenPDF emits indents as x-offsets relative to the left margin.
        assertTrue(content.contains(offset(ScreenplayLayout.CHARACTER_INDENT_IN) + " -"),
                "character cue should be offset " + ScreenplayLayout.CHARACTER_INDENT_IN
                        + "in from the margin; content was:\n" + content);
        assertTrue(content.contains(offset(ScreenplayLayout.DIALOGUE_INDENT_IN) + " -"),
                "dialogue should be offset " + ScreenplayLayout.DIALOGUE_INDENT_IN + "in from the margin");
        assertTrue(content.contains(offset(ScreenplayLayout.PARENTHETICAL_INDENT_IN) + " -"),
                "parenthetical should be offset " + ScreenplayLayout.PARENTHETICAL_INDENT_IN + "in from the margin");
    }

    @Test
    void pdfLeavesABlankLineBetweenElementsAndTwoBeforeAScene() throws Exception {
        byte[] pdf = PdfExportServiceImpl.toPdf(null, sampleBlocks());
        String content = pdfTextOperators(pdf);

        // Vertical moves are "<dx> -<dy> Td". The scene break is the largest one.
        double largestDrop = 0;
        Matcher m = Pattern.compile("([-\\d.]+) -([\\d.]+) Td").matcher(content);
        while (m.find()) {
            largestDrop = Math.max(largestDrop, Double.parseDouble(m.group(2)));
        }
        assertEquals(ScreenplayLayout.SCENE_SPACING_PT, largestDrop, 0.5,
                "the largest vertical move should be the scene-heading break");
    }

    // --- DOCX ---------------------------------------------------------------

    @Test
    void docxPageGeometryMatchesTheLayout() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(
                DocxExportServiceImpl.toDocx(null, sampleBlocks())))) {
            CTPageMar pgMar = doc.getDocument().getBody().getSectPr().getPgMar();
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.MARGIN_LEFT_IN),
                    asInt(pgMar.getLeft()), "left gutter");
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.MARGIN_RIGHT_IN),
                    asInt(pgMar.getRight()), "right margin");

            CTPageSz pgSz = doc.getDocument().getBody().getSectPr().getPgSz();
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.PAGE_WIDTH_IN), asInt(pgSz.getW()));
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.PAGE_HEIGHT_IN), asInt(pgSz.getH()));
        }
    }

    @Test
    void docxIndentsAndRhythmMatchTheLayout() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(
                DocxExportServiceImpl.toDocx(null, sampleBlocks())))) {
            XWPFParagraph scene = paragraphContaining(doc, "INT. KITCHEN");
            XWPFParagraph action = paragraphContaining(doc, "Jane enters");
            XWPFParagraph character = paragraphContaining(doc, "JANE");
            XWPFParagraph paren = paragraphContaining(doc, "(quietly)");
            XWPFParagraph dialogue = paragraphContaining(doc, "Hello there");

            // POI reports an unset indent as -1, which Word renders as 0.
            assertTrue(action.getIndentationLeft() <= 0,
                    "action runs the full column, got " + action.getIndentationLeft());
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.CHARACTER_INDENT_IN),
                    character.getIndentationLeft(), "character indent");
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.PARENTHETICAL_INDENT_IN),
                    paren.getIndentationLeft(), "parenthetical indent");
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.DIALOGUE_INDENT_IN),
                    dialogue.getIndentationLeft(), "dialogue indent");

            // Widths are expressed as an inset from the right margin.
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.TEXT_WIDTH_IN
                            - ScreenplayLayout.DIALOGUE_INDENT_IN - ScreenplayLayout.DIALOGUE_WIDTH_IN),
                    dialogue.getIndentationRight(), "dialogue is 3.5in wide");
            assertEquals(ScreenplayLayout.twips(ScreenplayLayout.TEXT_WIDTH_IN
                            - ScreenplayLayout.PARENTHETICAL_INDENT_IN - ScreenplayLayout.PARENTHETICAL_WIDTH_IN),
                    paren.getIndentationRight(), "parenthetical is 2in wide");

            // Rhythm: carried by spacingBefore, in twips.
            assertEquals(ScreenplayLayout.twipsFromPoints(ScreenplayLayout.SCENE_SPACING_PT),
                    scene.getSpacingBefore(), "scene headings get the fuller break");
            assertEquals(ScreenplayLayout.twipsFromPoints(ScreenplayLayout.ELEMENT_SPACING_PT),
                    character.getSpacingBefore(), "a blank line precedes the character cue");
            assertEquals(ScreenplayLayout.twipsFromPoints(ScreenplayLayout.SPEECH_GROUP_SPACING_PT),
                    paren.getSpacingBefore(), "parentheticals hug the cue");
            assertEquals(ScreenplayLayout.twipsFromPoints(ScreenplayLayout.SPEECH_GROUP_SPACING_PT),
                    dialogue.getSpacingBefore(), "dialogue hugs the cue");
        }
    }

    // --- FDX ----------------------------------------------------------------

    @Test
    void fdxElementSettingsUseLayoutGeometryFromThePageEdge() {
        String xml = FdxExportServiceImpl.toFdx(null, sampleBlocks());

        // FDX measures from the page edge, so the body starts at the gutter.
        assertTrue(xml.contains("Type=\"Action\">\n    <FontSpec Font=\"Courier Final Draft\" Size=\"12\"")
                        || xml.contains("Size=\"12\""),
                "Courier 12pt");
        assertParagraphSpec(xml, "Action",
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.ACTION_INDENT_IN),
                ScreenplayLayout.rightTextEdgeFromPageEdge(),
                ScreenplayLayout.ELEMENT_SPACING_PT);
        assertParagraphSpec(xml, "Character",
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.CHARACTER_INDENT_IN),
                ScreenplayLayout.rightTextEdgeFromPageEdge(),
                ScreenplayLayout.ELEMENT_SPACING_PT);
        assertParagraphSpec(xml, "Parenthetical",
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.PARENTHETICAL_INDENT_IN),
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.PARENTHETICAL_INDENT_IN)
                        + ScreenplayLayout.PARENTHETICAL_WIDTH_IN,
                ScreenplayLayout.SPEECH_GROUP_SPACING_PT);
        assertParagraphSpec(xml, "Dialogue",
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.DIALOGUE_INDENT_IN),
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.DIALOGUE_INDENT_IN)
                        + ScreenplayLayout.DIALOGUE_WIDTH_IN,
                ScreenplayLayout.SPEECH_GROUP_SPACING_PT);
        assertParagraphSpec(xml, "Scene Heading",
                ScreenplayLayout.fromPageEdge(ScreenplayLayout.ACTION_INDENT_IN),
                ScreenplayLayout.rightTextEdgeFromPageEdge(),
                ScreenplayLayout.SCENE_SPACING_PT);
    }

    @Test
    void fdxKeepsLyricsItalicLikeTheOtherExporters() {
        Block lyrics = block(Block.TYPE_LYRICS, "La la la", null);
        String xml = FdxExportServiceImpl.toFdx(null, List.of(lyrics));
        assertTrue(xml.contains("Style=\"Italic\""),
                "lyrics carry italics in PDF, DOCX and EPUB; FDX should agree. Got:\n" + xml);
    }

    // --- Helpers ------------------------------------------------------------

    private static void assertParagraphSpec(String xml, String type, double left, double right,
                                            double spaceBefore) {
        Matcher m = Pattern.compile(
                "<ElementSettings Type=\"" + Pattern.quote(type) + "\">.*?"
                        + "LeftIndent=\"([\\d.]+)\" RightIndent=\"([\\d.]+)\" SpaceBefore=\"([\\d.]+)\"",
                Pattern.DOTALL).matcher(xml);
        assertTrue(m.find(), "no ElementSettings for " + type + " in:\n" + xml);
        assertEquals(left, Double.parseDouble(m.group(1)), 1e-6, type + " left indent");
        assertEquals(right, Double.parseDouble(m.group(2)), 1e-6, type + " right indent");
        assertEquals(spaceBefore, Double.parseDouble(m.group(3)), 1e-6, type + " space before");
    }

    /** POI's generated accessors return Object or BigInteger depending on version. */
    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    /** Offsets appear in the PDF content stream as points, e.g. "158.4". */
    private static String offset(double inches) {
        float points = ScreenplayLayout.pt(inches);
        return points == Math.rint(points)
                ? String.valueOf((int) points)
                : String.valueOf(points);
    }

    private static XWPFParagraph paragraphContaining(XWPFDocument doc, String needle) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            String text = p.getText();
            if (text != null && text.contains(needle)) {
                return p;
            }
        }
        throw new AssertionError("no paragraph containing '" + needle + "'");
    }

    /** Inflates every content stream in the PDF and concatenates the text operators. */
    private static String pdfTextOperators(byte[] pdf) throws Exception {
        assertNotNull(pdf);
        StringBuilder all = new StringBuilder();
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        Matcher m = Pattern.compile("stream\r?\n").matcher(raw);
        while (m.find()) {
            int start = m.end();
            int end = raw.indexOf("endstream", start);
            if (end < 0) {
                continue;
            }
            byte[] chunk = raw.substring(start, end).getBytes(StandardCharsets.ISO_8859_1);
            try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(chunk))) {
                all.append(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1)).append('\n');
            } catch (Exception ignored) {
                // Not a deflated content stream (fonts, metadata) — skip it.
            }
        }
        return all.toString();
    }

    private static List<Block> sampleBlocks() {
        Person jane = new Person();
        jane.setName("Jane");
        List<Block> blocks = new ArrayList<>();
        blocks.add(block(Block.TYPE_ACTION, "Jane enters.", null));
        blocks.add(block(Block.TYPE_CHARACTER, "JANE", jane));
        blocks.add(block(Block.TYPE_PARENTHETICAL, "quietly", null));
        blocks.add(block(Block.TYPE_DIALOGUE, "Hello there.", jane));
        blocks.add(block(Block.TYPE_SCENE, "INT. KITCHEN - DAY", null));
        blocks.add(block(Block.TYPE_ACTION, "She waits.", null));
        return blocks;
    }

    private static Block block(String type, String content, Person person) {
        Block block = new Block();
        block.setType(type);
        block.setContent(content);
        block.setPerson(person);
        return block;
    }
}
