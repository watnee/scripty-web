package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
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

    // Geometry is shared with the PDF and FDX exporters — see ScreenplayLayout.
    private static final long PAGE_WIDTH = ScreenplayLayout.twips(ScreenplayLayout.PAGE_WIDTH_IN);
    private static final long PAGE_HEIGHT = ScreenplayLayout.twips(ScreenplayLayout.PAGE_HEIGHT_IN);
    private static final long LEFT_MARGIN = ScreenplayLayout.twips(ScreenplayLayout.MARGIN_LEFT_IN);
    private static final long RIGHT_MARGIN = ScreenplayLayout.twips(ScreenplayLayout.MARGIN_RIGHT_IN);
    private static final long TOP_MARGIN = ScreenplayLayout.twips(ScreenplayLayout.MARGIN_TOP_IN);
    private static final long BOTTOM_MARGIN = ScreenplayLayout.twips(ScreenplayLayout.MARGIN_BOTTOM_IN);

    private static final int CHARACTER_INDENT = ScreenplayLayout.twips(ScreenplayLayout.CHARACTER_INDENT_IN);
    private static final int DIALOGUE_INDENT = ScreenplayLayout.twips(ScreenplayLayout.DIALOGUE_INDENT_IN);
    private static final int PARENTHETICAL_INDENT = ScreenplayLayout.twips(ScreenplayLayout.PARENTHETICAL_INDENT_IN);

    // POI takes an inset from the right margin rather than a width.
    private static final int DIALOGUE_RIGHT = ScreenplayLayout.twips(
            ScreenplayLayout.TEXT_WIDTH_IN - ScreenplayLayout.DIALOGUE_INDENT_IN - ScreenplayLayout.DIALOGUE_WIDTH_IN);
    private static final int PARENTHETICAL_RIGHT = ScreenplayLayout.twips(
            ScreenplayLayout.TEXT_WIDTH_IN - ScreenplayLayout.PARENTHETICAL_INDENT_IN
                    - ScreenplayLayout.PARENTHETICAL_WIDTH_IN);

    // setSpacingBefore/After take twips, not points — the previous 40 here was
    // 2pt, a sixth of the intended blank line.
    private static final int ELEMENT_SPACING = ScreenplayLayout.twipsFromPoints(ScreenplayLayout.ELEMENT_SPACING_PT);
    private static final int SCENE_SPACING = ScreenplayLayout.twipsFromPoints(ScreenplayLayout.SCENE_SPACING_PT);
    private static final int SPEECH_GROUP_SPACING =
            ScreenplayLayout.twipsFromPoints(ScreenplayLayout.SPEECH_GROUP_SPACING_PT);

    private static final int FONT_SIZE_POINTS = (int) ScreenplayLayout.FONT_SIZE_PT;
    private static final String FONT = "Courier New";

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
    public byte[] exportProject(Integer projectId, Integer editionId, CapitalizationPreferences capsOrNull) {
        CapitalizationPreferences caps = capsOrNull != null ? capsOrNull : CapitalizationPreferences.ALL;
        Project project = projectRepository.findById(projectId).orElse(null);
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId);

        try {
            return toDocx(project, blocks);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export project " + projectId + " as DOCX", e);
        }
    }

    /** Package-private seam so the layout can be asserted without a Spring context. */
    static byte[] toDocx(Project project, List<Block> blocks) throws Exception {
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
                if (appendBlock(document, block, caps)) {
                    wroteBody = true;
                }
            }

            if (!wroteTitlePage && !wroteBody) {
                addRun(document.createParagraph(), " ", Font.NORMAL);
            }

            document.write(out);
            return out.toByteArray();
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
        String version = project.getScreenplayVersion();
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasWriters = writers != null && !writers.isBlank();
        boolean hasContact = contact != null && !contact.isBlank();
        boolean hasVersion = version != null && !version.isBlank();
        if (!hasTitle && !hasWriters && !hasContact && !hasVersion) {
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

        if (hasVersion) {
            XWPFParagraph versionPara = document.createParagraph();
            versionPara.setAlignment(ParagraphAlignment.CENTER);
            versionPara.setSpacingBefore(hasWriters ? 180 : 0);
            versionPara.setSpacingAfter(20);
            addRun(versionPara, version.trim(), Font.NORMAL);
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

    private static boolean appendBlock(XWPFDocument document, Block block, CapitalizationPreferences caps) {
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
                p.setSpacingBefore(SCENE_SPACING);
                addRun(p, caps.apply(content.trim(), type), styleFlags(block, Font.BOLD));
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
                addRun(p, caps.apply(name.trim(), type), styleFlags(block, Font.BOLD));
            }
            case Block.TYPE_DIALOGUE -> {
                XWPFParagraph p = bodyParagraph(document);
                p.setIndentationLeft(DIALOGUE_INDENT);
                p.setIndentationRight(DIALOGUE_RIGHT);
                p.setSpacingBefore(SPEECH_GROUP_SPACING);
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
                p.setSpacingBefore(SPEECH_GROUP_SPACING);
                addRun(p, paren, styleFlags(block, Font.ITALIC));
            }
            case Block.TYPE_TRANSITION -> {
                XWPFParagraph p = bodyParagraph(document);
                p.setAlignment(ParagraphAlignment.RIGHT);
                addRun(p, caps.apply(content.trim(), type), styleFlags(block, Font.NORMAL));
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
        paragraph.setSpacingBefore(ELEMENT_SPACING);
        paragraph.setSpacingAfter(0);
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
        run.setFontSize(FONT_SIZE_POINTS);
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
