package com.scripty.service;

import com.scripty.dto.Block;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Screenplay element types recognized from Fountain screenplay syntax
 * (https://fountain.io/syntax). Used to label blocks by what kind of
 * screenplay element their content represents.
 */
public enum FountainElement {

    SCENE_HEADING("Scene Heading"),
    ACTION("Action"),
    CHARACTER("Character"),
    DIALOGUE("Dialogue"),
    PARENTHETICAL("Parenthetical"),
    TRANSITION("Transition"),
    LYRIC("Lyric"),
    CENTERED("Centered"),
    SECTION("Section"),
    SYNOPSIS("Synopsis");

    private final String label;

    FountainElement(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    private static final Pattern SCENE_HEADING_PATTERN =
        Pattern.compile("(?i)^(INT|EXT|EST|INT\\.?/EXT|I/E)[.\\s].*");

    /**
     * Classify block content into a Fountain element. {@code hasPerson} is true
     * when the block already has a character attached (typed as an uppercase
     * character line or "@Name", or selected in the form), which makes the
     * remaining content dialogue.
     */
    public static FountainElement classify(String content, boolean hasPerson) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return hasPerson ? DIALOGUE : ACTION;
        }

        String[] lines = trimmed.split("\n", -1);
        String firstLine = lines[0].trim();

        if (hasPerson) {
            if (lines.length == 1 && isParenthetical(firstLine)) return PARENTHETICAL;
            return DIALOGUE;
        }

        // Forced elements take priority over pattern matching
        if (firstLine.startsWith("!")) return ACTION;
        if (firstLine.startsWith("@")) return CHARACTER;
        if (firstLine.startsWith(".") && !firstLine.startsWith("..")) return SCENE_HEADING;
        if (firstLine.startsWith(">") && firstLine.endsWith("<")) return CENTERED;
        if (firstLine.startsWith(">")) return TRANSITION;
        if (firstLine.startsWith("~")) return LYRIC;
        if (firstLine.startsWith("#")) return SECTION;
        if (firstLine.startsWith("=") && !firstLine.startsWith("===")) return SYNOPSIS;

        if (SCENE_HEADING_PATTERN.matcher(firstLine).matches()) return SCENE_HEADING;
        if (isUppercase(firstLine) && firstLine.endsWith("TO:")) return TRANSITION;
        if (lines.length == 1 && isUppercase(firstLine)) return CHARACTER;
        // Uppercase character cue followed directly by dialogue text
        if (lines.length > 1 && isUppercase(firstLine) && !lines[1].trim().isEmpty()) return DIALOGUE;
        if (lines.length == 1 && isParenthetical(firstLine)) return PARENTHETICAL;

        return ACTION;
    }

    /**
     * Classify all blocks of a scene in order, applying context rules that a
     * single block can't decide on its own:
     * - a parenthetical only counts inside dialogue (after a character cue,
     *   dialogue, or another parenthetical); standalone it is action
     * - a lone uppercase line is only a character cue when dialogue-capable
     *   content follows; otherwise it is action
     * - plain text directly after a character cue or parenthetical is dialogue
     *
     * Blocks with a manual label keep it and still provide context for their
     * neighbors. With {@code treatStoredAsFixed} any stored label is kept
     * (used at display time, where stored labels were already computed in
     * context and only legacy unlabeled blocks need filling in).
     */
    public static FountainElement[] classifyScene(List<Block> blocks, boolean treatStoredAsFixed) {
        int n = blocks.size();
        FountainElement[] base = new FountainElement[n];
        boolean[] locked = new boolean[n];
        for (int i = 0; i < n; i++) {
            Block block = blocks.get(i);
            FountainElement stored = fromLabel(block.getElement());
            if (stored != null && (block.isElementManual() || treatStoredAsFixed)) {
                base[i] = stored;
                locked[i] = true;
            } else {
                base[i] = classify(block.getContent(), block.getPerson() != null);
            }
        }

        FountainElement[] result = new FountainElement[n];
        for (int i = 0; i < n; i++) {
            FountainElement el = base[i];
            if (!locked[i]) {
                Block block = blocks.get(i);
                boolean hasPerson = block.getPerson() != null;
                String first = firstLine(block.getContent());
                FountainElement prev = i > 0 ? result[i - 1] : null;
                boolean inDialogue = prev == CHARACTER || prev == DIALOGUE || prev == PARENTHETICAL;
                if (el == PARENTHETICAL && !hasPerson && !inDialogue) {
                    el = ACTION;
                } else if (el == CHARACTER && !first.startsWith("@")
                        && !isDialogueCapable(i + 1 < n ? blocks.get(i + 1) : null,
                                              i + 1 < n ? base[i + 1] : null)) {
                    el = ACTION;
                } else if (el == ACTION && !first.startsWith("!")
                        && (prev == CHARACTER || prev == PARENTHETICAL)) {
                    el = DIALOGUE;
                }
            }
            result[i] = el;
        }
        return result;
    }

    private static boolean isDialogueCapable(Block next, FountainElement nextBase) {
        if (next == null) return false;
        if (nextBase == DIALOGUE || nextBase == PARENTHETICAL) return true;
        return nextBase == ACTION && !firstLine(next.getContent()).startsWith("!");
    }

    private static String firstLine(String content) {
        String trimmed = content == null ? "" : content.trim();
        int idx = trimmed.indexOf('\n');
        return idx < 0 ? trimmed : trimmed.substring(0, idx).trim();
    }

    /** Returns the element with the given display label, or null if none matches. */
    public static FountainElement fromLabel(String label) {
        if (label == null) return null;
        for (FountainElement element : values()) {
            if (element.label.equalsIgnoreCase(label.trim())) {
                return element;
            }
        }
        return null;
    }

    /**
     * Returns the stored element label, or classifies the content on the fly
     * for blocks saved before element labeling existed.
     */
    public static String labelFor(String storedElement, String content, boolean hasPerson) {
        if (storedElement != null && !storedElement.trim().isEmpty()) {
            return storedElement;
        }
        return classify(content, hasPerson).getLabel();
    }

    private static boolean isParenthetical(String line) {
        return line.startsWith("(") && line.endsWith(")");
    }

    private static boolean isUppercase(String line) {
        return line.equals(line.toUpperCase()) && line.matches(".*[A-Z].*");
    }
}
