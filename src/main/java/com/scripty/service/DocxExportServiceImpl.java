package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Industry-style US Letter screenplay DOCX (Courier New 12pt), matching PDF/print layout.
 */
@Service
public class DocxExportServiceImpl implements DocxExportService {

    // Twips: 1440 = 1 inch
    private static final long TWIPS_PER_INCH = 1440L;
    private static final long PAGE_WIDTH = 12240L;   // 8.5in
    private static final long PAGE_HEIGHT = 15840L;  // 11in
    private static final long LEFT_MARGIN = 2160L;   // 1.5in
    private static final long RIGHT_MARGIN = 1440L;  // 1in
    private static final long TOP_MARGIN = 1440L;    // 1in
    private static final long BOTTOM_MARGIN = 1440L; // 1in

    private static final int CHARACTER_INDENT = twips(2.2);
    private static final int DIALOGUE_INDENT = twips(1.0);
    private static final int DIALOGUE_RIGHT = twips(1.5);       // ~3.5in wide on 6in action area
    private static final int PARENTHETICAL_INDENT = twips(1.5);
    private static final int PARENTHETICAL_RIGHT = twips(2.5);  // ~2in wide

    private static final int FONT_SIZE_HALF_POINTS = 24; // 12pt
    private static final String FONT = "Courier New";

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        List<Block> blocks = blockRepository.findByProjectIdOrderByOrderAsc(projectId);

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            configurePage(document);

            boolean wroteTitlePage = appendTitlePage(document, project);
            boolean wroteBody = false;

            if (wroteTitlePage && hasExportableBody(blocks)) {
                XWPFParagraph breakPara = document.createParagraph();
                XWPFRun breakRun = breakPara.createRun();
                breakRun.addBreak(BreakType.PAGE);
            }

            for (Block block : blocks) {
                if (appendBlock(document, block)) {
                    wroteBody = true;
                }
            }

            if (!wroteTitlePage && !wroteBody) {
                addRun(document.createParagraph(), " ", Font.NORMAL);
            }

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export project " + projectId + " as DOCX", e);
        }
    }

    private static void configurePage(XWPFDocument document) {
        CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();

        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(PAGE_WIDTH));
        pageSize.setH(BigInteger.valueOf(PAGE_HEIGHT));
        pageSize.setOrient(STPageOrientation.PORTRAIT);

        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setLeft(BigInteger.valueOf(LEFT_MARGIN));
        pageMar.setRight(BigInteger.valueOf(RIGHT_MARGIN));
        pageMar.setTop(BigInteger.valueOf(TOP_MARGIN));
        pageMar.setBottom(BigInteger.valueOf(BOTTOM_MARGIN));
    }

    private static boolean appendTitlePage(XWPFDocument document, Project project) {
        if (project == null) {
            return false;
        }
        String title = firstNonBlank(project.getScreenplayTitle(), project.getTitle());
        String writers = project.getWriters();
        String contact = project.getContactInfo();
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasWriters = writers != null && !writers.isBlank();
        boolean hasContact = contact != null && !contact.isBlank();
        if (!hasTitle && !hasWriters && !hasContact) {
            return false;
        }

        // Approximate vertical centering for the title block
        for (int i = 0; i < 10; i++) {
            document.createParagraph();
        }

        if (hasTitle) {
            XWPFParagraph titlePara = document.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            titlePara.setSpacingAfter(240);
            addRun(titlePara, title.trim().toUpperCase(Locale.ROOT), Font.BOLD);
        }

        if (hasWriters) {
            String[] writerLines = writers.trim().split("\n", -1);
            int authorStart = 0;
            String first = writerLines[0].trim();
            if (!first.isEmpty() && first.matches("(?i)^(?:written(?:\\s+and\\s+directed)?\\s+by|story\\s+by|screenplay\\s+by|by)\\.?$")) {
                XWPFParagraph credit = document.createParagraph();
                credit.setAlignment(ParagraphAlignment.CENTER);
                credit.setSpacingAfter(60);
                addRun(credit, first, Font.NORMAL);
                authorStart = 1;
            } else {
                XWPFParagraph credit = document.createParagraph();
                credit.setAlignment(ParagraphAlignment.CENTER);
                credit.setSpacingAfter(60);
                addRun(credit, "written by", Font.NORMAL);
            }
            for (int i = authorStart; i < writerLines.length; i++) {
                String line = writerLines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                XWPFParagraph author = document.createParagraph();
                author.setAlignment(ParagraphAlignment.CENTER);
                author.setSpacingAfter(20);
                addRun(author, line, Font.NORMAL);
            }
        }

        if (hasContact) {
            for (int i = 0; i < 14; i++) {
                document.createParagraph();
            }
            for (String line : contact.trim().split("\n", -1)) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                XWPFParagraph contactLine = document.createParagraph();
                contactLine.setAlignment(ParagraphAlignment.LEFT);
                contactLine.setSpacingAfter(20);
                addRun(contactLine, line.trim(), Font.NORMAL);
            }
        }

        return true;
    }

    private static boolean hasExportableBody(List<Block> blocks) {
        for (Block block : blocks) {
            String type = block.getType();
            if (!Block.TYPE_SECTION.equals(type)
                    && !Block.TYPE_SYNOPSIS.equals(type)
                    && !Block.TYPE_NOTE.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean appendBlock(XWPFDocument document, Block block) {
        String type = block.getType();
        if (Block.TYPE_SECTION.equals(type)
                || Block.TYPE_SYNOPSIS.equals(type)
                || Block.TYPE_NOTE.equals(type)) {
            return false;
        }

        String content = block.getContent() != null ? block.getContent() : "";

        switch (type) {
            case Block.TYPE_SCENE, Block.TYPE_SHOT -> {
                XWPFParagraph p = bodyParagraph(document);
                addRun(p, content.trim().toUpperCase(Locale.ROOT), styleFlags(block, Font.BOLD));
            }
            case Block.TYPE_ACTION, Block.TYPE_TEXT -> {
                XWPFParagraph p = bodyParagraph(document);
                addMultilineRun(p, content, styleFlags(block, Font.NORMAL));
            }
            case Block.TYPE_LYRICS -> {
                XWPFParagraph p = bodyParagraph(document);
                addMultilineRun(p, content, styleFlags(block, Font.ITALIC));
            }
            case Block.TYPE_CHARACTER, Block.TYPE_DUAL_DIALOGUE -> {
                String name = block.getPerson() != null ? block.getPerson().getName() : content;
                if (name == null || name.isBlank()) {
                    return false;
                }
                XWPFParagraph p = bodyParagraph(document);
                p.setIndentationLeft(CHARACTER_INDENT);
                p.setSpacingAfter(0);
                addRun(p, name.trim().toUpperCase(Locale.ROOT), styleFlags(block, Font.BOLD));
            }
            case Block.TYPE_DIALOGUE -> {
                XWPFParagraph p = bodyParagraph(document);
                p.setIndentationLeft(DIALOGUE_INDENT);
                p.setIndentationRight(DIALOGUE_RIGHT);
                p.setSpacingBefore(0);
                addMultilineRun(p, content, styleFlags(block, Font.NORMAL));
            }
            case Block.TYPE_PARENTHETICAL -> {
                String paren = content.trim();
                if (!(paren.startsWith("(") && paren.endsWith(")"))) {
                    paren = "(" + content + ")";
                }
                XWPFParagraph p = bodyParagraph(document);
                p.setIndentationLeft(PARENTHETICAL_INDENT);
                p.setIndentationRight(PARENTHETICAL_RIGHT);
                p.setSpacingBefore(0);
                p.setSpacingAfter(0);
                addRun(p, paren, styleFlags(block, Font.ITALIC));
            }
            case Block.TYPE_TRANSITION -> {
                XWPFParagraph p = bodyParagraph(document);
                p.setAlignment(ParagraphAlignment.RIGHT);
                addRun(p, content.trim().toUpperCase(Locale.ROOT), styleFlags(block, Font.NORMAL));
            }
            case Block.TYPE_CENTERED -> {
                XWPFParagraph p = bodyParagraph(document);
                p.setAlignment(ParagraphAlignment.CENTER);
                addMultilineRun(p, content, styleFlags(block, Font.NORMAL));
            }
            case Block.TYPE_PAGE_BREAK -> {
                XWPFParagraph breakPara = document.createParagraph();
                XWPFRun breakRun = breakPara.createRun();
                breakRun.addBreak(BreakType.PAGE);
            }
            default -> {
                XWPFParagraph p = bodyParagraph(document);
                addMultilineRun(p, content, styleFlags(block, Font.NORMAL));
            }
        }
        return true;
    }

    private static XWPFParagraph bodyParagraph(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingBefore(40);
        paragraph.setSpacingAfter(40);
        paragraph.setSpacingBetween(1.0);
        return paragraph;
    }

    private static void addMultilineRun(XWPFParagraph paragraph, String text, int style) {
        if (text == null || text.isEmpty()) {
            addRun(paragraph, " ", style);
            return;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                XWPFRun br = paragraph.createRun();
                applyFont(br, style);
                br.addBreak();
            }
            addRun(paragraph, lines[i].isEmpty() ? " " : lines[i], style);
        }
    }

    private static void addRun(XWPFParagraph paragraph, String text, int style) {
        XWPFRun run = paragraph.createRun();
        applyFont(run, style);
        run.setText(text == null ? "" : text);
    }

    private static void applyFont(XWPFRun run, int style) {
        run.setFontFamily(FONT);
        run.setFontSize(FONT_SIZE_HALF_POINTS / 2);
        run.setBold((style & Font.BOLD) != 0);
        run.setItalic((style & Font.ITALIC) != 0);
        run.setUnderline((style & Font.UNDERLINE) != 0
                ? org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE
                : org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE);
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

    private static int twips(double inches) {
        return (int) Math.round(inches * TWIPS_PER_INCH);
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

    /** Local style bit flags (mirrors OpenPDF Font constants without a dependency). */
    private static final class Font {
        static final int NORMAL = 0;
        static final int BOLD = 1;
        static final int ITALIC = 2;
        static final int UNDERLINE = 4;

        private Font() {
        }
    }
}
