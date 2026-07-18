package com.scripty.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfWriter;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Industry-style US Letter screenplay PDF (Courier 12pt), matching print CSS margins
 * and element indents.
 */
@Service
public class PdfExportServiceImpl implements PdfExportService {

    private static final float LEFT_MARGIN = 108f;   // 1.5in
    private static final float RIGHT_MARGIN = 72f;   // 1in
    private static final float TOP_MARGIN = 72f;     // 1in
    private static final float BOTTOM_MARGIN = 72f;  // 1in

    private static final float CHARACTER_INDENT = 158.4f;      // 2.2in
    private static final float DIALOGUE_INDENT = 72f;          // 1in
    private static final float DIALOGUE_RIGHT = 108f;          // keep dialogue ~3.5in wide
    private static final float PARENTHETICAL_INDENT = 108f;    // 1.5in
    private static final float PARENTHETICAL_RIGHT = 216f;     // ~2in wide

    private static final float ELEMENT_SPACING = 3.6f; // ~0.05in

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        return exportProject(projectId, null, CapitalizationPreferences.ALL);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId, Integer editionId) {
        return exportProject(projectId, editionId, CapitalizationPreferences.ALL);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId, Integer editionId, CapitalizationPreferences caps) {
        CapitalizationPreferences capitalization = caps != null ? caps : CapitalizationPreferences.ALL;
        Project project = projectRepository.findById(projectId).orElse(null);
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAsc(projectId);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.LETTER, LEFT_MARGIN, RIGHT_MARGIN, TOP_MARGIN, BOTTOM_MARGIN);
            PdfWriter.getInstance(document, out);
            document.open();

            boolean wroteTitlePage = appendTitlePage(document, project);
            boolean wroteBody = false;
            if (wroteTitlePage && !blocks.isEmpty()) {
                document.newPage();
            }

            for (Block block : blocks) {
                if (appendBlock(document, block, capitalization)) {
                    wroteBody = true;
                }
            }

            if (!wroteTitlePage && !wroteBody) {
                // Empty script — still produce a valid one-page PDF
                document.add(new Paragraph(" ", courier(Font.NORMAL)));
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export project " + projectId + " as PDF", e);
        }
    }

    private static boolean appendTitlePage(Document document, Project project) throws DocumentException {
        if (project == null) {
            return false;
        }
        String title = firstNonBlank(project.getScreenplayTitle(), project.getTitle());
        String writers = project.getWriters();
        String contact = project.getContactInfo();
        String version = project.getScreenplayVersion();
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasWriters = writers != null && !writers.isBlank();
        boolean hasContact = contact != null && !contact.isBlank();
        boolean hasVersion = version != null && !version.isBlank();
        if (!hasTitle && !hasWriters && !hasContact && !hasVersion) {
            return false;
        }

        // Vertical centering approximation: push title block down the page
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(180f);
        document.add(spacer);

        if (hasTitle) {
            Paragraph titlePara = styledParagraph(title.trim().toUpperCase(Locale.ROOT), Font.BOLD);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(24f);
            document.add(titlePara);
        }

        if (hasWriters) {
            String[] writerLines = writers.trim().split("\n", -1);
            int authorStart = 0;
            String first = writerLines[0].trim();
            if (!first.isEmpty() && first.matches("(?i)^(?:written(?:\\s+and\\s+directed)?\\s+by|story\\s+by|screenplay\\s+by|by)\\.?$")) {
                Paragraph credit = styledParagraph(first, Font.NORMAL);
                credit.setAlignment(Element.ALIGN_CENTER);
                credit.setSpacingAfter(6f);
                document.add(credit);
                authorStart = 1;
            } else {
                Paragraph credit = styledParagraph("written by", Font.NORMAL);
                credit.setAlignment(Element.ALIGN_CENTER);
                credit.setSpacingAfter(6f);
                document.add(credit);
            }
            for (int i = authorStart; i < writerLines.length; i++) {
                String line = writerLines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                Paragraph author = styledParagraph(line, Font.NORMAL);
                author.setAlignment(Element.ALIGN_CENTER);
                author.setSpacingAfter(2f);
                document.add(author);
            }
        }

        if (hasVersion) {
            Paragraph versionPara = styledParagraph(version.trim(), Font.NORMAL);
            versionPara.setAlignment(Element.ALIGN_CENTER);
            versionPara.setSpacingBefore(hasWriters ? 18f : 0f);
            versionPara.setSpacingAfter(2f);
            document.add(versionPara);
        }

        if (hasContact) {
            Paragraph contactSpacer = new Paragraph(" ");
            contactSpacer.setSpacingBefore(280f);
            document.add(contactSpacer);
            for (String line : contact.trim().split("\n", -1)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                Paragraph contactLine = styledParagraph(line.trim(), Font.NORMAL);
                contactLine.setAlignment(Element.ALIGN_LEFT);
                contactLine.setSpacingAfter(2f);
                document.add(contactLine);
            }
        }

        return true;
    }

    private static boolean appendBlock(Document document, Block block, CapitalizationPreferences caps)
            throws DocumentException {
        String type = block.getType();
        if (Block.TYPE_SECTION.equals(type)
                || Block.TYPE_SYNOPSIS.equals(type)
                || Block.TYPE_NOTE.equals(type)) {
            // Match print CSS: omit outline/meta elements from the printed script
            return false;
        }

        String content = block.getContent() != null ? block.getContent() : "";

        switch (type) {
            case Block.TYPE_SCENE, Block.TYPE_SHOT -> {
                Paragraph p = styledParagraph(caps.apply(content.trim(), type), styleFlags(block, Font.BOLD));
                p.setSpacingBefore(ELEMENT_SPACING);
                p.setSpacingAfter(ELEMENT_SPACING);
                document.add(p);
            }
            case Block.TYPE_ACTION, Block.TYPE_TEXT, Block.TYPE_LYRICS -> {
                int flags = styleFlags(block, Block.TYPE_LYRICS.equals(type) ? Font.ITALIC : Font.NORMAL);
                Paragraph p = styledParagraph(content, flags);
                p.setSpacingBefore(ELEMENT_SPACING);
                p.setSpacingAfter(ELEMENT_SPACING);
                document.add(p);
            }
            case Block.TYPE_CHARACTER, Block.TYPE_DUAL_DIALOGUE -> {
                String name = block.getPerson() != null ? block.getPerson().getName() : content;
                if (name == null || name.isBlank()) {
                    return false;
                }
                Paragraph p = styledParagraph(caps.apply(name.trim(), type), styleFlags(block, Font.BOLD));
                p.setIndentationLeft(CHARACTER_INDENT);
                p.setSpacingBefore(ELEMENT_SPACING);
                p.setSpacingAfter(0f);
                document.add(p);
            }
            case Block.TYPE_DIALOGUE -> {
                Paragraph p = styledParagraph(content, styleFlags(block, Font.NORMAL));
                p.setIndentationLeft(DIALOGUE_INDENT);
                p.setIndentationRight(DIALOGUE_RIGHT);
                p.setSpacingBefore(0f);
                p.setSpacingAfter(ELEMENT_SPACING);
                document.add(p);
            }
            case Block.TYPE_PARENTHETICAL -> {
                String paren = content.trim();
                if (!(paren.startsWith("(") && paren.endsWith(")"))) {
                    paren = "(" + content + ")";
                }
                Paragraph p = styledParagraph(paren, styleFlags(block, Font.ITALIC));
                p.setIndentationLeft(PARENTHETICAL_INDENT);
                p.setIndentationRight(PARENTHETICAL_RIGHT);
                p.setSpacingBefore(0f);
                p.setSpacingAfter(0f);
                document.add(p);
            }
            case Block.TYPE_TRANSITION -> {
                Paragraph p = styledParagraph(caps.apply(content.trim(), type), styleFlags(block, Font.NORMAL));
                p.setAlignment(Element.ALIGN_RIGHT);
                p.setSpacingBefore(ELEMENT_SPACING);
                p.setSpacingAfter(ELEMENT_SPACING);
                document.add(p);
            }
            case Block.TYPE_CENTERED -> {
                Paragraph p = styledParagraph(content, styleFlags(block, Font.NORMAL));
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingBefore(ELEMENT_SPACING);
                p.setSpacingAfter(ELEMENT_SPACING);
                document.add(p);
            }
            case Block.TYPE_PAGE_BREAK -> {
                document.newPage();
                return true;
            }
            default -> {
                Paragraph p = styledParagraph(content, styleFlags(block, Font.NORMAL));
                p.setSpacingBefore(ELEMENT_SPACING);
                p.setSpacingAfter(ELEMENT_SPACING);
                document.add(p);
            }
        }
        return true;
    }

    private static Paragraph styledParagraph(String text, int style) {
        Font font = courier(style);
        Paragraph paragraph = new Paragraph();
        paragraph.setFont(font);
        paragraph.setLeading(12f);
        if (text == null || text.isEmpty()) {
            paragraph.add(new Chunk(" ", font));
            return paragraph;
        }
        // Preserve intentional blank lines within a block
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                paragraph.add(Chunk.NEWLINE);
            }
            paragraph.add(new Phrase(lines[i].isEmpty() ? " " : lines[i], font));
        }
        return paragraph;
    }

    private static int styleFlags(Block block, int base) {
        int flags = base;
        if (block.isTextBold()) {
            flags |= Font.BOLD;
        }
        if (block.isTextItalic()) {
            flags |= Font.ITALIC;
        }
        if (block.isTextUnderline()) {
            flags |= Font.UNDERLINE;
        }
        return flags;
    }

    private static Font courier(int style) {
        return FontFactory.getFont(FontFactory.COURIER, 12f, style);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
