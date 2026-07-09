package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.junit.jupiter.api.Test;

class DocxToFountainConverterTest {

    private static final long TWIPS_PER_INCH = 1440L;
    private static final int CHARACTER_INDENT = (int) Math.round(2.2 * TWIPS_PER_INCH);
    private static final int DIALOGUE_INDENT = (int) Math.round(1.0 * TWIPS_PER_INCH);
    private static final int DIALOGUE_RIGHT = (int) Math.round(1.5 * TWIPS_PER_INCH);
    private static final int PARENTHETICAL_INDENT = (int) Math.round(1.5 * TWIPS_PER_INCH);
    private static final int PARENTHETICAL_RIGHT = (int) Math.round(2.5 * TWIPS_PER_INCH);

    @Test
    void convertsScreenplayLayoutToFountain() throws Exception {
        byte[] docx = buildSampleDocx();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            assertTrue(DocxToFountainConverter.looksLikeScreenplayLayout(document));
            String fountain = DocxToFountainConverter.convert(document);

            assertTrue(fountain.contains("Title: MY SCRIPT"));
            assertTrue(fountain.contains("Credit: written by"));
            assertTrue(fountain.contains("Author: Jane Doe"));
            assertTrue(fountain.contains("Contact: jane@example.com"));
            assertTrue(fountain.contains("INT. KITCHEN - DAY"));
            assertTrue(fountain.contains("@JANE"));
            assertTrue(fountain.contains("(quietly)"));
            assertTrue(fountain.contains("Hello there."));
            assertTrue(fountain.contains("CUT TO:"));

            int sep = fountain.indexOf("\n\n");
            String body = sep >= 0 ? fountain.substring(sep + 2) : fountain;
            FountainImportServiceImpl importer = new FountainImportServiceImpl();
            List<?> parsed = importer.parse(body);
            assertEquals(6, parsed.size());
        }
    }

    @Test
    void plainDocWithoutIndentsIsNotScreenplayLayout() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph p = document.createParagraph();
            XWPFRun run = p.createRun();
            run.setText("Just a normal Word document.");
            assertFalse(DocxToFountainConverter.looksLikeScreenplayLayout(document));
        }
    }

    private static byte[] buildSampleDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            configurePage(document);

            for (int i = 0; i < 2; i++) {
                document.createParagraph();
            }

            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            addRun(title, "MY SCRIPT", true, false);

            XWPFParagraph credit = document.createParagraph();
            credit.setAlignment(ParagraphAlignment.CENTER);
            addRun(credit, "written by", false, false);

            XWPFParagraph author = document.createParagraph();
            author.setAlignment(ParagraphAlignment.CENTER);
            addRun(author, "Jane Doe", false, false);

            XWPFParagraph contact = document.createParagraph();
            contact.setAlignment(ParagraphAlignment.LEFT);
            addRun(contact, "jane@example.com", false, false);

            XWPFParagraph pageBreak = document.createParagraph();
            pageBreak.createRun().addBreak(BreakType.PAGE);

            XWPFParagraph scene = document.createParagraph();
            addRun(scene, "INT. KITCHEN - DAY", true, false);

            XWPFParagraph action = document.createParagraph();
            addRun(action, "Jane enters.", false, false);

            XWPFParagraph character = document.createParagraph();
            character.setIndentationLeft(CHARACTER_INDENT);
            addRun(character, "JANE", true, false);

            XWPFParagraph paren = document.createParagraph();
            paren.setIndentationLeft(PARENTHETICAL_INDENT);
            paren.setIndentationRight(PARENTHETICAL_RIGHT);
            addRun(paren, "(quietly)", false, true);

            XWPFParagraph dialogue = document.createParagraph();
            dialogue.setIndentationLeft(DIALOGUE_INDENT);
            dialogue.setIndentationRight(DIALOGUE_RIGHT);
            addRun(dialogue, "Hello there.", false, false);

            XWPFParagraph transition = document.createParagraph();
            transition.setAlignment(ParagraphAlignment.RIGHT);
            addRun(transition, "CUT TO:", false, false);

            document.write(out);
            return out.toByteArray();
        }
    }

    private static void configurePage(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(12240L));
        pageSize.setH(BigInteger.valueOf(15840L));
        pageSize.setOrient(STPageOrientation.PORTRAIT);
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setLeft(BigInteger.valueOf(2160L));
        pageMar.setRight(BigInteger.valueOf(1440L));
        pageMar.setTop(BigInteger.valueOf(1440L));
        pageMar.setBottom(BigInteger.valueOf(1440L));
    }

    private static void addRun(XWPFParagraph paragraph, String text, boolean bold, boolean italic) {
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Courier New");
        run.setFontSize(12);
        run.setBold(bold);
        run.setItalic(italic);
        run.setText(text);
    }
}
