package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfToFountainConverterTest {

    private static final float LEFT_MARGIN = 108f;
    private static final float RIGHT_MARGIN = 72f;
    private static final float TOP_MARGIN = 72f;
    private static final float BOTTOM_MARGIN = 72f;
    private static final float CHARACTER_INDENT = 158.4f;
    private static final float DIALOGUE_INDENT = 72f;
    private static final float DIALOGUE_RIGHT = 108f;
    private static final float PARENTHETICAL_INDENT = 108f;
    private static final float PARENTHETICAL_RIGHT = 216f;

    @Test
    void convertsScreenplayLayoutToFountain() throws Exception {
        byte[] pdf = buildSamplePdf();
        String fountain = PdfToFountainConverter.convert(new ByteArrayInputStream(pdf));

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

    @Test
    void convertPlainExtractsLines() throws Exception {
        byte[] pdf = buildSamplePdf();
        String plain = PdfToFountainConverter.convertPlain(new ByteArrayInputStream(pdf));

        assertTrue(plain.contains("MY SCRIPT"));
        assertTrue(plain.contains("INT. KITCHEN - DAY"));
        assertTrue(plain.contains("JANE"));
        assertTrue(plain.contains("Hello there."));
        assertTrue(!plain.contains("@JANE"));
    }

    @Test
    void looksLikePdfDetectsExtensionAndMime() {
        assertTrue(PdfToFountainConverter.looksLikePdf("script.pdf", ""));
        assertTrue(PdfToFountainConverter.looksLikePdf("script.pdf", "application/octet-stream"));
        assertTrue(PdfToFountainConverter.looksLikePdf("upload", "application/pdf"));
    }

    private static byte[] buildSamplePdf() throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER, LEFT_MARGIN, RIGHT_MARGIN, TOP_MARGIN, BOTTOM_MARGIN);
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingBefore(180f);
            document.add(spacer);

            Paragraph title = styled("MY SCRIPT", Font.BOLD);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(24f);
            document.add(title);

            Paragraph credit = styled("written by", Font.NORMAL);
            credit.setAlignment(Element.ALIGN_CENTER);
            credit.setSpacingAfter(6f);
            document.add(credit);

            Paragraph author = styled("Jane Doe", Font.NORMAL);
            author.setAlignment(Element.ALIGN_CENTER);
            author.setSpacingAfter(2f);
            document.add(author);

            Paragraph contactSpacer = new Paragraph(" ");
            contactSpacer.setSpacingBefore(280f);
            document.add(contactSpacer);

            Paragraph contact = styled("jane@example.com", Font.NORMAL);
            contact.setAlignment(Element.ALIGN_LEFT);
            document.add(contact);

            document.newPage();

            Paragraph scene = styled("INT. KITCHEN - DAY", Font.BOLD);
            scene.setSpacingBefore(3.6f);
            scene.setSpacingAfter(3.6f);
            document.add(scene);

            Paragraph action = styled("Jane enters.", Font.NORMAL);
            action.setSpacingBefore(3.6f);
            action.setSpacingAfter(3.6f);
            document.add(action);

            Paragraph character = styled("JANE", Font.BOLD);
            character.setIndentationLeft(CHARACTER_INDENT);
            character.setSpacingBefore(3.6f);
            character.setSpacingAfter(0f);
            document.add(character);

            Paragraph paren = styled("(quietly)", Font.ITALIC);
            paren.setIndentationLeft(PARENTHETICAL_INDENT);
            paren.setIndentationRight(PARENTHETICAL_RIGHT);
            paren.setSpacingBefore(0f);
            paren.setSpacingAfter(0f);
            document.add(paren);

            Paragraph dialogue = styled("Hello there.", Font.NORMAL);
            dialogue.setIndentationLeft(DIALOGUE_INDENT);
            dialogue.setIndentationRight(DIALOGUE_RIGHT);
            dialogue.setSpacingBefore(0f);
            dialogue.setSpacingAfter(3.6f);
            document.add(dialogue);

            Paragraph transition = styled("CUT TO:", Font.NORMAL);
            transition.setAlignment(Element.ALIGN_RIGHT);
            transition.setSpacingBefore(3.6f);
            transition.setSpacingAfter(3.6f);
            document.add(transition);

            document.close();
            return out.toByteArray();
        }
    }

    private static Paragraph styled(String text, int style) {
        Font font = FontFactory.getFont(FontFactory.COURIER, 12f, style);
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setLeading(12f);
        return paragraph;
    }
}
