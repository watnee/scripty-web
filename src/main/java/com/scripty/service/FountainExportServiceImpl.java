package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FountainExportServiceImpl implements FountainExportService {

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

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Override
    @Transactional(readOnly = true)
    public String exportProject(Integer projectId) {
        return exportProject(projectId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public String exportProject(Integer projectId, Integer editionId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        ScriptEdition edition = scriptEditionService.requireForProject(projectId, editionId);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId);
        StringBuilder sb = new StringBuilder();

        if (project != null) {
            appendTitlePage(sb, project);
        }

        String activeCharacter = null;

        for (Block block : blocks) {
            String type = block.getType();
            String content = block.getContent() != null ? block.getContent() : "";

            switch (type) {
                case Block.TYPE_SCENE -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendScene(sb, content, block);
                }
                case Block.TYPE_ACTION -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendAction(sb, content, block);
                }
                case Block.TYPE_TEXT -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, applyEmphasis(content, block));
                }
                case Block.TYPE_CHARACTER -> {
                    String characterName = block.getPerson() != null ? block.getPerson().getName() : content;
                    if (characterName != null && !characterName.isBlank()) {
                        appendBlankLine(sb);
                        appendCharacterCue(sb, characterName, false, block);
                        activeCharacter = characterName;
                    }
                }
                case Block.TYPE_DUAL_DIALOGUE -> {
                    String characterName = block.getPerson() != null ? block.getPerson().getName() : content;
                    if (characterName != null && !characterName.isBlank()) {
                        appendBlankLine(sb);
                        appendCharacterCue(sb, characterName, true, block);
                        activeCharacter = characterName;
                    }
                }
                case Block.TYPE_DIALOGUE -> {
                    String characterName = block.getPerson() != null ? block.getPerson().getName() : activeCharacter;
                    if (characterName != null && !characterName.equalsIgnoreCase(activeCharacter)) {
                        appendBlankLine(sb);
                        appendCharacterCue(sb, characterName, false, null);
                        activeCharacter = characterName;
                    } else if (activeCharacter == null) {
                        appendBlankLine(sb);
                    }
                    for (String line : content.split("\n", -1)) {
                        appendLine(sb, applyEmphasis(line, block));
                    }
                }
                case Block.TYPE_PARENTHETICAL -> {
                    String paren = content.trim();
                    if (!(paren.startsWith("(") && paren.endsWith(")"))) {
                        paren = "(" + content + ")";
                    }
                    appendLine(sb, applyEmphasis(paren, block));
                }
                case Block.TYPE_TRANSITION -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendTransition(sb, content, block);
                }
                case Block.TYPE_SHOT -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendShot(sb, content, block);
                }
                case Block.TYPE_LYRICS -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "~" + applyEmphasis(content, block));
                }
                case Block.TYPE_CENTERED -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, ">" + applyEmphasis(content, block) + "<");
                }
                case Block.TYPE_SECTION -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "#" + applyEmphasis(content, block));
                }
                case Block.TYPE_SYNOPSIS -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "=" + applyEmphasis(content, block));
                }
                case Block.TYPE_NOTE -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "[[" + content + "]]");
                }
                case Block.TYPE_PAGE_BREAK -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "===");
                }
                default -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, applyEmphasis(content, block));
                }
            }
        }

        String exported = sb.toString().stripTrailing();
        return exported.isEmpty() ? "" : exported + "\n";
    }

    private static void appendTitlePage(StringBuilder sb, Project project) {
        String title = project.getScreenplayTitle();
        if (title == null || title.isBlank()) {
            title = project.getTitle();
        }
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

        if (hasTitle) {
            appendLine(sb, "Title: " + title.trim());
        }
        if (hasWriters) {
            appendWriters(sb, writers.trim());
        }
        if (hasVersion) {
            // Fountain's conventional key for draft/revision labels
            appendLine(sb, "Draft date: " + version.trim());
        }
        if (hasContact) {
            String[] contactLines = contact.trim().split("\n", -1);
            appendLine(sb, "Contact: " + contactLines[0].trim());
            for (int i = 1; i < contactLines.length; i++) {
                String line = contactLines[i].trim();
                if (!line.isEmpty()) {
                    appendLine(sb, line);
                }
            }
        }
        // Blank line separates title page from script body (Fountain spec)
        sb.append('\n');
    }

    /**
     * Round-trips import's Credit+Author merge: a credit-like first line
     * ({@code Written by}, {@code Story by}, …) becomes {@code Credit:}; remaining
     * lines (or the whole field) become {@code Author:}.
     */
    private static void appendWriters(StringBuilder sb, String writers) {
        String[] writerLines = writers.split("\n", -1);
        int authorStart = 0;
        String first = writerLines[0].trim();
        if (!first.isEmpty() && CREDIT_LINE.matcher(first).matches()) {
            appendLine(sb, "Credit: " + first);
            authorStart = 1;
        }
        if (authorStart >= writerLines.length) {
            return;
        }
        String firstAuthor = writerLines[authorStart].trim();
        boolean hasMoreAuthors = false;
        for (int i = authorStart + 1; i < writerLines.length; i++) {
            if (!writerLines[i].trim().isEmpty()) {
                hasMoreAuthors = true;
                break;
            }
        }
        if (firstAuthor.isEmpty() && !hasMoreAuthors) {
            return;
        }
        appendLine(sb, "Author: " + firstAuthor);
        for (int i = authorStart + 1; i < writerLines.length; i++) {
            String line = writerLines[i].trim();
            if (!line.isEmpty()) {
                appendLine(sb, line);
            }
        }
    }

    private static void appendScene(StringBuilder sb, String content, Block block) {
        if (content.isBlank()) {
            appendLine(sb, ".");
            return;
        }
        String body = applyEmphasis(content.trim(), block);
        if (SCENE_HEADING.matcher(content.trim()).matches()) {
            appendLine(sb, body);
        } else {
            appendLine(sb, "." + body);
        }
    }

    private static void appendAction(StringBuilder sb, String content, Block block) {
        String trimmed = content.trim();
        String body = applyEmphasis(content, block);
        if (needsForcedAction(trimmed)) {
            if (!body.startsWith("!")) {
                body = "!" + body;
            }
        }
        appendLine(sb, body);
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
        // All-caps short lines parse as character cues
        return looksLikeCharacterCue(trimmed);
    }

    private static void appendCharacterCue(StringBuilder sb, String characterName, boolean dual, Block block) {
        String name = characterName.trim();
        String exported = name.toUpperCase(Locale.ROOT);
        if (needsForcedCharacter(exported)) {
            exported = "@" + exported;
        }
        if (dual) {
            exported = exported + " ^";
        }
        appendLine(sb, applyEmphasis(exported, block));
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
        // Names that wouldn't survive import's character heuristic
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

    private static void appendTransition(StringBuilder sb, String content, Block block) {
        String trimmed = content.trim();
        String body = applyEmphasis(trimmed, block);
        if (TRANSITION.matcher(trimmed).matches()) {
            appendLine(sb, body);
        } else {
            appendLine(sb, ">" + body);
        }
    }

    private static void appendShot(StringBuilder sb, String content, Block block) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            appendLine(sb, "CLOSE ON");
            return;
        }
        // Fountain has no forced-shot marker; export as uppercase so it reads as a shot.
        appendLine(sb, applyEmphasis(trimmed.toUpperCase(Locale.ROOT), block));
    }

    /**
     * Applies block-level Fountain emphasis markers when the whole element is styled.
     * Bold {@code **}, italic {@code *}, underline {@code _}; nested as {@code _***text***_}.
     */
    private static String applyEmphasis(String text, Block block) {
        if (block == null || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        boolean bold = block.isTextBold();
        boolean italic = block.isTextItalic();
        boolean underline = block.isTextUnderline();
        if (!bold && !italic && !underline) {
            return text;
        }
        // Skip if content already uses Fountain emphasis wrappers end-to-end
        String trimmed = text.trim();
        if ((trimmed.startsWith("***") && trimmed.endsWith("***"))
                || (trimmed.startsWith("**") && trimmed.endsWith("**"))
                || (trimmed.startsWith("*") && trimmed.endsWith("*") && !trimmed.startsWith("**"))
                || (trimmed.startsWith("_") && trimmed.endsWith("_"))) {
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
