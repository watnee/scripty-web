package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.repository.BlockRepository;
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

    @Autowired
    private BlockRepository blockRepository;

    @Override
    @Transactional(readOnly = true)
    public String exportProject(Integer projectId) {
        List<Block> blocks = blockRepository.findByProjectIdOrderByOrderAsc(projectId);
        StringBuilder sb = new StringBuilder();
        String activeCharacter = null;

        for (Block block : blocks) {
            String type = block.getType();
            String content = block.getContent() != null ? block.getContent() : "";

            switch (type) {
                case Block.TYPE_SCENE -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendScene(sb, content);
                }
                case Block.TYPE_ACTION -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, content);
                }
                case Block.TYPE_CHARACTER -> {
                    String characterName = block.getPerson() != null ? block.getPerson().getName() : content;
                    if (characterName != null && !characterName.isBlank()) {
                        appendBlankLine(sb);
                        appendLine(sb, characterName.toUpperCase(Locale.ROOT));
                        activeCharacter = characterName;
                    }
                }
                case Block.TYPE_DUAL_DIALOGUE -> {
                    String characterName = block.getPerson() != null ? block.getPerson().getName() : content;
                    if (characterName != null && !characterName.isBlank()) {
                        appendBlankLine(sb);
                        appendLine(sb, characterName.toUpperCase(Locale.ROOT) + " ^");
                        activeCharacter = characterName;
                    }
                }
                case Block.TYPE_DIALOGUE -> {
                    String characterName = block.getPerson() != null ? block.getPerson().getName() : activeCharacter;
                    if (characterName != null && !characterName.equalsIgnoreCase(activeCharacter)) {
                        appendBlankLine(sb);
                        appendLine(sb, characterName.toUpperCase(Locale.ROOT));
                        activeCharacter = characterName;
                    } else if (activeCharacter == null) {
                        appendBlankLine(sb);
                    }
                    for (String line : content.split("\n", -1)) {
                        appendLine(sb, line);
                    }
                }
                case Block.TYPE_PARENTHETICAL -> appendLine(sb, "(" + content + ")");
                case Block.TYPE_TRANSITION -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendTransition(sb, content);
                }
                case Block.TYPE_SHOT -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendShot(sb, content);
                }
                case Block.TYPE_LYRICS -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "~" + content);
                }
                case Block.TYPE_CENTERED -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, ">" + content + "<");
                }
                case Block.TYPE_SECTION -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "#" + content);
                }
                case Block.TYPE_SYNOPSIS -> {
                    activeCharacter = null;
                    appendBlankLine(sb);
                    appendLine(sb, "=" + content);
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
                    appendLine(sb, content);
                }
            }
        }

        return sb.toString().stripTrailing();
    }

    private static void appendScene(StringBuilder sb, String content) {
        if (content.isBlank()) {
            appendLine(sb, ".");
            return;
        }
        if (SCENE_HEADING.matcher(content.trim()).matches()) {
            appendLine(sb, content.trim());
        } else {
            appendLine(sb, "." + content.trim());
        }
    }

    private static void appendTransition(StringBuilder sb, String content) {
        String trimmed = content.trim();
        if (TRANSITION.matcher(trimmed).matches()) {
            appendLine(sb, trimmed);
        } else {
            appendLine(sb, ">" + trimmed);
        }
    }

    private static void appendShot(StringBuilder sb, String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            appendLine(sb, "CLOSE ON");
            return;
        }
        if (SHOT.matcher(trimmed).matches()) {
            appendLine(sb, trimmed.toUpperCase(Locale.ROOT));
        } else {
            // Fountain has no forced-shot marker; export as uppercase action so it reads as a shot.
            appendLine(sb, trimmed.toUpperCase(Locale.ROOT));
        }
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
        sb.append(line);
    }
}
