package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports a project as Final Draft {@code .fdx} XML (DocumentType Script, Version 3).
 */
@Service
public class FdxExportServiceImpl implements FdxExportService {

    private static final Pattern CREDIT_LINE = Pattern.compile(
            "^(?:written(?:\\s+and\\s+directed)?\\s+by|story\\s+by|screenplay\\s+by|by)\\.?$",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId) {
        return exportProject(projectId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProject(Integer projectId, Integer editionId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        return toFdx(project, blocks).getBytes(StandardCharsets.UTF_8);
    }

    /** Package-visible for unit tests. */
    static String toFdx(Project project, List<Block> blocks) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n");
        xml.append("<FinalDraft DocumentType=\"Script\" Template=\"No\" Version=\"3\">\n");

        appendTitlePage(xml, project);

        xml.append("  <Content>\n");
        boolean wroteBody = false;
        boolean nextStartsNewPage = false;
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (Block.TYPE_PAGE_BREAK.equals(block.getType())) {
                nextStartsNewPage = true;
                wroteBody = true;
                continue;
            }
            if (Block.TYPE_SECTION.equals(block.getType())
                    || Block.TYPE_SYNOPSIS.equals(block.getType())) {
                continue;
            }
            if (appendBlock(xml, blocks, i, nextStartsNewPage)) {
                wroteBody = true;
                nextStartsNewPage = false;
            }
            // Skip the dual-dialogue partner already consumed by appendBlock
            if (Block.TYPE_CHARACTER.equals(block.getType())
                    && i + 1 < blocks.size()
                    && isDualDialogueStart(blocks, i)) {
                i = skipDualDialoguePair(blocks, i);
            }
        }
        if (!wroteBody) {
            appendParagraph(xml, "Action", null, false, " ", false);
        }
        xml.append("  </Content>\n");

        appendElementSettings(xml);
        xml.append("</FinalDraft>\n");
        return xml.toString();
    }

    private static void appendTitlePage(StringBuilder xml, Project project) {
        if (project == null) {
            return;
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
            return;
        }

        xml.append("  <TitlePage>\n");
        xml.append("    <Content>\n");

        if (hasTitle) {
            appendTitleParagraph(xml, "Center", title.trim().toUpperCase(Locale.ROOT));
        }
        if (hasWriters) {
            String[] writerLines = writers.trim().split("\n", -1);
            int authorStart = 0;
            String first = writerLines[0].trim();
            if (!first.isEmpty() && CREDIT_LINE.matcher(first).matches()) {
                appendTitleParagraph(xml, "Center", first);
                authorStart = 1;
            } else {
                appendTitleParagraph(xml, "Center", "written by");
            }
            for (int i = authorStart; i < writerLines.length; i++) {
                String line = writerLines[i].trim();
                if (!line.isEmpty()) {
                    appendTitleParagraph(xml, "Center", line);
                }
            }
        }
        if (hasVersion) {
            appendTitleParagraph(xml, "Center", version.trim());
        }
        if (hasContact) {
            for (String line : contact.trim().split("\n", -1)) {
                if (!line.trim().isEmpty()) {
                    appendTitleParagraph(xml, "Left", line.trim());
                }
            }
        }

        xml.append("    </Content>\n");
        xml.append("  </TitlePage>\n");
    }

    private static void appendTitleParagraph(StringBuilder xml, String alignment, String text) {
        xml.append("      <Paragraph Alignment=\"").append(alignment).append("\">\n");
        xml.append("        <Text>").append(escapeXml(text)).append("</Text>\n");
        xml.append("      </Paragraph>\n");
    }

    /**
     * @return true if a paragraph was written
     */
    private static boolean appendBlock(StringBuilder xml, List<Block> blocks, int index,
                                       boolean startsNewPage) {
        Block block = blocks.get(index);
        String type = block.getType();
        String content = block.getContent() != null ? block.getContent() : "";

        if (Block.TYPE_CHARACTER.equals(type) && isDualDialogueStart(blocks, index)) {
            return appendDualDialogue(xml, blocks, index);
        }

        switch (type) {
            case Block.TYPE_SCENE -> {
                appendParagraph(xml, "Scene Heading", block, false,
                        content.trim().toUpperCase(Locale.ROOT), startsNewPage);
                return true;
            }
            case Block.TYPE_ACTION, Block.TYPE_TEXT -> {
                appendParagraph(xml, "Action", block, false, content, startsNewPage);
                return true;
            }
            case Block.TYPE_LYRICS -> {
                // FDX has no lyrics element, so lyrics ride along as Action —
                // but italic, matching every other exporter.
                appendParagraph(xml, "Action", block, false, content, startsNewPage, true);
                return true;
            }
            case Block.TYPE_CHARACTER -> {
                String name = block.getPerson() != null ? block.getPerson().getName() : content;
                if (name == null || name.isBlank()) {
                    return false;
                }
                appendParagraph(xml, "Character", block, false,
                        name.trim().toUpperCase(Locale.ROOT), startsNewPage);
                return true;
            }
            case Block.TYPE_DUAL_DIALOGUE -> {
                String name = block.getPerson() != null ? block.getPerson().getName() : content;
                if (name == null || name.isBlank()) {
                    return false;
                }
                appendParagraph(xml, "Character", block, false,
                        name.trim().toUpperCase(Locale.ROOT), startsNewPage);
                return true;
            }
            case Block.TYPE_DIALOGUE -> {
                appendParagraph(xml, "Dialogue", block, false, content, startsNewPage);
                return true;
            }
            case Block.TYPE_PARENTHETICAL -> {
                String paren = content.trim();
                if (!(paren.startsWith("(") && paren.endsWith(")"))) {
                    paren = "(" + content + ")";
                }
                appendParagraph(xml, "Parenthetical", block, false, paren, startsNewPage);
                return true;
            }
            case Block.TYPE_TRANSITION -> {
                appendParagraph(xml, "Transition", block, false,
                        content.trim().toUpperCase(Locale.ROOT), startsNewPage);
                return true;
            }
            case Block.TYPE_SHOT -> {
                appendParagraph(xml, "Shot", block, false,
                        content.trim().toUpperCase(Locale.ROOT), startsNewPage);
                return true;
            }
            case Block.TYPE_CENTERED -> {
                appendParagraph(xml, "General", block, true, content, startsNewPage);
                return true;
            }
            case Block.TYPE_NOTE -> {
                appendNoteParagraph(xml, content, startsNewPage);
                return true;
            }
            default -> {
                appendParagraph(xml, "Action", block, false, content, startsNewPage);
                return true;
            }
        }
    }

    private static boolean isDualDialogueStart(List<Block> blocks, int index) {
        // CHARACTER ... (optional paren/dialogue) then DUAL_DIALOGUE
        for (int i = index + 1; i < blocks.size(); i++) {
            String type = blocks.get(i).getType();
            if (Block.TYPE_DUAL_DIALOGUE.equals(type)) {
                return true;
            }
            if (Block.TYPE_PARENTHETICAL.equals(type) || Block.TYPE_DIALOGUE.equals(type)) {
                continue;
            }
            return false;
        }
        return false;
    }

    private static int skipDualDialoguePair(List<Block> blocks, int characterIndex) {
        int i = characterIndex + 1;
        // first speaker's paren/dialogue
        while (i < blocks.size()
                && (Block.TYPE_PARENTHETICAL.equals(blocks.get(i).getType())
                || Block.TYPE_DIALOGUE.equals(blocks.get(i).getType()))) {
            i++;
        }
        if (i < blocks.size() && Block.TYPE_DUAL_DIALOGUE.equals(blocks.get(i).getType())) {
            i++;
            while (i < blocks.size()
                    && (Block.TYPE_PARENTHETICAL.equals(blocks.get(i).getType())
                    || Block.TYPE_DIALOGUE.equals(blocks.get(i).getType()))) {
                i++;
            }
        }
        return i - 1; // caller increments
    }

    private static boolean appendDualDialogue(StringBuilder xml, List<Block> blocks, int characterIndex) {
        xml.append("    <Paragraph>\n");

        // Side 1: CHARACTER + following paren/dialogue until DUAL_DIALOGUE
        xml.append("      <DualDialogue>\n");
        appendSideParagraph(xml, "Character", blocks.get(characterIndex));
        int i = characterIndex + 1;
        while (i < blocks.size()
                && (Block.TYPE_PARENTHETICAL.equals(blocks.get(i).getType())
                || Block.TYPE_DIALOGUE.equals(blocks.get(i).getType()))) {
            String type = Block.TYPE_PARENTHETICAL.equals(blocks.get(i).getType())
                    ? "Parenthetical" : "Dialogue";
            appendSideParagraph(xml, type, blocks.get(i));
            i++;
        }
        xml.append("      </DualDialogue>\n");

        // Side 2: DUAL_DIALOGUE + following paren/dialogue
        if (i < blocks.size() && Block.TYPE_DUAL_DIALOGUE.equals(blocks.get(i).getType())) {
            xml.append("      <DualDialogue>\n");
            appendSideParagraph(xml, "Character", blocks.get(i));
            i++;
            while (i < blocks.size()
                    && (Block.TYPE_PARENTHETICAL.equals(blocks.get(i).getType())
                    || Block.TYPE_DIALOGUE.equals(blocks.get(i).getType()))) {
                String type = Block.TYPE_PARENTHETICAL.equals(blocks.get(i).getType())
                        ? "Parenthetical" : "Dialogue";
                appendSideParagraph(xml, type, blocks.get(i));
                i++;
            }
            xml.append("      </DualDialogue>\n");
        }

        xml.append("    </Paragraph>\n");
        return true;
    }

    private static void appendSideParagraph(StringBuilder xml, String fdxType, Block block) {
        String content = blockContentForType(block, fdxType);
        xml.append("        <Paragraph Type=\"").append(fdxType).append("\">\n");
        appendTextRuns(xml, content, block, "          ");
        xml.append("        </Paragraph>\n");
    }

    private static String blockContentForType(Block block, String fdxType) {
        String content = block.getContent() != null ? block.getContent() : "";
        if ("Character".equals(fdxType)) {
            String name = block.getPerson() != null ? block.getPerson().getName() : content;
            return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        }
        if ("Parenthetical".equals(fdxType)) {
            String paren = content.trim();
            if (!(paren.startsWith("(") && paren.endsWith(")"))) {
                paren = "(" + content + ")";
            }
            return paren;
        }
        return content;
    }

    private static void appendNoteParagraph(StringBuilder xml, String content, boolean startsNewPage) {
        xml.append("    <Paragraph Type=\"Action\"");
        if (startsNewPage) {
            xml.append(" StartsNewPage=\"Yes\"");
        }
        xml.append(">\n");
        xml.append("      <Text></Text>\n");
        xml.append("      <ScriptNote>\n");
        xml.append("        <Paragraph>\n");
        xml.append("          <Text>").append(escapeXml(content == null ? "" : content)).append("</Text>\n");
        xml.append("        </Paragraph>\n");
        xml.append("      </ScriptNote>\n");
        xml.append("    </Paragraph>\n");
    }

    private static void appendParagraph(StringBuilder xml, String fdxType, Block block,
                                        boolean centered, String content, boolean startsNewPage) {
        appendParagraph(xml, fdxType, block, centered, content, startsNewPage, false);
    }

    private static void appendParagraph(StringBuilder xml, String fdxType, Block block,
                                        boolean centered, String content, boolean startsNewPage,
                                        boolean baseItalic) {
        xml.append("    <Paragraph Type=\"").append(fdxType).append("\"");
        if (centered) {
            xml.append(" Alignment=\"Center\"");
        }
        if (startsNewPage) {
            xml.append(" StartsNewPage=\"Yes\"");
        }
        xml.append(">\n");
        appendTextRuns(xml, content == null ? "" : content, block, "      ", baseItalic);
        xml.append("    </Paragraph>\n");
    }

    private static void appendTextRuns(StringBuilder xml, String content, Block block, String indent) {
        appendTextRuns(xml, content, block, indent, false);
    }

    private static void appendTextRuns(StringBuilder xml, String content, Block block, String indent,
                                       boolean baseItalic) {
        String style = styleAttribute(block, baseItalic);
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        xml.append(indent).append("<Text");
        if (!style.isEmpty()) {
            xml.append(" Style=\"").append(style).append('"');
        }
        xml.append(">").append(escapeXml(normalized)).append("</Text>\n");
    }

    private static String styleAttribute(Block block) {
        return styleAttribute(block, false);
    }

    private static String styleAttribute(Block block, boolean baseItalic) {
        if (block == null) {
            return baseItalic ? "Italic" : "";
        }
        StringBuilder style = new StringBuilder();
        if (block.isTextBold()) {
            style.append("Bold");
        }
        if (baseItalic || block.isTextItalic()) {
            if (!style.isEmpty()) {
                style.append('+');
            }
            style.append("Italic");
        }
        if (block.isTextUnderline()) {
            if (!style.isEmpty()) {
                style.append('+');
            }
            style.append("Underline");
        }
        return style.toString();
    }

    private static void appendElementSettings(StringBuilder xml) {
        // ElementSettings so Final Draft recognizes standard element types and
        // lays them out like the PDF/DOCX exports. FDX indents are measured from
        // the page's left edge, not from the margin, so they run through
        // ScreenplayLayout.fromPageEdge(). These previously implied 1.25in
        // symmetric margins, shifting the whole body a quarter inch left of the
        // 1.5in gutter every other surface uses.
        double textLeft = ScreenplayLayout.fromPageEdge(ScreenplayLayout.ACTION_INDENT_IN);
        double textRight = ScreenplayLayout.rightTextEdgeFromPageEdge();
        double characterLeft = ScreenplayLayout.fromPageEdge(ScreenplayLayout.CHARACTER_INDENT_IN);
        double parenLeft = ScreenplayLayout.fromPageEdge(ScreenplayLayout.PARENTHETICAL_INDENT_IN);
        double dialogueLeft = ScreenplayLayout.fromPageEdge(ScreenplayLayout.DIALOGUE_INDENT_IN);

        double element = ScreenplayLayout.ELEMENT_SPACING_PT;
        double scene = ScreenplayLayout.SCENE_SPACING_PT;
        double speechGroup = ScreenplayLayout.SPEECH_GROUP_SPACING_PT;

        appendElementSetting(xml, "General", "Left", textLeft, textRight, "", element);
        appendElementSetting(xml, "Scene Heading", "Left", textLeft, textRight, "AllCaps", scene);
        appendElementSetting(xml, "Action", "Left", textLeft, textRight, "", element);
        appendElementSetting(xml, "Character", "Left", characterLeft, textRight, "AllCaps", element);
        appendElementSetting(xml, "Parenthetical", "Left", parenLeft,
                parenLeft + ScreenplayLayout.PARENTHETICAL_WIDTH_IN, "", speechGroup);
        appendElementSetting(xml, "Dialogue", "Left", dialogueLeft,
                dialogueLeft + ScreenplayLayout.DIALOGUE_WIDTH_IN, "", speechGroup);
        appendElementSetting(xml, "Transition", "Right", textLeft, textRight, "AllCaps", element);
        appendElementSetting(xml, "Shot", "Left", textLeft, textRight, "AllCaps", element);
    }

    private static void appendElementSetting(StringBuilder xml, String type, String alignment,
                                             double left, double right, String fontStyle,
                                             double spaceBefore) {
        xml.append("  <ElementSettings Type=\"").append(type).append("\">\n");
        xml.append("    <FontSpec Font=\"Courier Final Draft\" Size=\"")
                .append(trimNumber(ScreenplayLayout.FONT_SIZE_PT)).append("\" Style=\"")
                .append(fontStyle).append("\"/>\n");
        xml.append("    <ParagraphSpec Alignment=\"").append(alignment)
                .append("\" LeftIndent=\"").append(trimNumber(left))
                .append("\" RightIndent=\"").append(trimNumber(right))
                .append("\" SpaceBefore=\"").append(trimNumber(spaceBefore))
                .append("\" Spacing=\"1\" StartsNewPage=\"No\"/>\n");
        xml.append("    <Behavior PaginateAs=\"").append(type).append("\"/>\n");
        xml.append("  </ElementSettings>\n");
    }

    /** Formats a measurement without a trailing ".0", which Final Draft dislikes. */
    private static String trimNumber(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private static String escapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    if (c == '\n') {
                        out.append("&#10;");
                    } else if (c == '\r') {
                        // skip
                    } else if (c < 0x20 && c != '\t') {
                        // skip other control chars
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
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
