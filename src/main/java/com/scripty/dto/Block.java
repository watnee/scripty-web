package com.scripty.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "`block`")
public class Block {

    // Fountain screenplay element types
    public static final String TYPE_SCENE = "SCENE";
    public static final String TYPE_ACTION = "ACTION";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_CHARACTER = "CHARACTER";
    public static final String TYPE_DIALOGUE = "DIALOGUE";
    public static final String TYPE_DUAL_DIALOGUE = "DUAL_DIALOGUE";
    public static final String TYPE_PARENTHETICAL = "PARENTHETICAL";
    public static final String TYPE_TRANSITION = "TRANSITION";
    public static final String TYPE_SHOT = "SHOT";
    public static final String TYPE_LYRICS = "LYRICS";
    public static final String TYPE_CENTERED = "CENTERED";
    public static final String TYPE_SECTION = "SECTION";
    public static final String TYPE_SYNOPSIS = "SYNOPSIS";
    public static final String TYPE_NOTE = "NOTE";
    public static final String TYPE_PAGE_BREAK = "PAGE_BREAK";

    public static final String ALIGN_LEFT = "LEFT";
    public static final String ALIGN_CENTER = "CENTER";
    public static final String ALIGN_RIGHT = "RIGHT";

    public static final java.util.Set<String> TEXT_ALIGNS = java.util.Set.of(
            ALIGN_LEFT, ALIGN_CENTER, ALIGN_RIGHT);

    public static final String STYLE_BOLD = "BOLD";
    public static final String STYLE_ITALIC = "ITALIC";
    public static final String STYLE_UNDERLINE = "UNDERLINE";

    public static final java.util.Set<String> TEXT_STYLES = java.util.Set.of(
            STYLE_BOLD, STYLE_ITALIC, STYLE_UNDERLINE);

    /** Character cue types whose content is the speaker name (including dual-dialogue cues). */
    public static final java.util.Set<String> CHARACTER_CUE_TYPES = java.util.Set.of(
            TYPE_CHARACTER, TYPE_DUAL_DIALOGUE);

    public static final java.util.Set<String> ELEMENT_TYPES = java.util.Set.of(
            TYPE_SCENE, TYPE_ACTION, TYPE_TEXT, TYPE_CHARACTER, TYPE_DIALOGUE, TYPE_DUAL_DIALOGUE,
            TYPE_PARENTHETICAL, TYPE_TRANSITION, TYPE_SHOT, TYPE_LYRICS, TYPE_CENTERED,
            TYPE_SECTION, TYPE_SYNOPSIS, TYPE_NOTE, TYPE_PAGE_BREAK);

    public static boolean isCharacterCueType(String type) {
        return type != null && CHARACTER_CUE_TYPES.contains(type);
    }

    public static String typeLabelFor(String type) {
        if (type == null || type.isBlank()) {
            return "Action";
        }
        String key = type.trim().toUpperCase();
        return switch (key) {
            case TYPE_SCENE -> "Scene";
            case TYPE_ACTION -> "Action";
            case TYPE_TEXT -> "Text";
            case TYPE_CHARACTER -> "Character";
            case TYPE_DIALOGUE -> "Dialogue";
            case TYPE_DUAL_DIALOGUE -> "Dual";
            case TYPE_PARENTHETICAL -> "(Paren)";
            case TYPE_TRANSITION -> "Transition";
            case TYPE_SHOT -> "Shot";
            case TYPE_LYRICS -> "Lyrics";
            case TYPE_CENTERED -> "Centered";
            case TYPE_SECTION -> "Section";
            case TYPE_SYNOPSIS -> "Synopsis";
            case TYPE_NOTE -> "Note";
            case TYPE_PAGE_BREAK -> "Page Break";
            default -> titleCaseTypeKey(key);
        };
    }

    private static String titleCaseTypeKey(String key) {
        String[] parts = key.replace('_', ' ').toLowerCase().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.length() > 0 ? out.toString() : "Action";
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "`order`", nullable = false)
    private Integer order;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean bookmarked;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "`type`", nullable = false)
    private String type = TYPE_ACTION;

    @Column(name = "scene_delimiter", nullable = false)
    private boolean sceneDelimiter = false;

    @Column(name = "text_align")
    private String textAlign;

    @Column(name = "text_bold", nullable = false)
    private boolean textBold = false;

    @Column(name = "text_italic", nullable = false)
    private boolean textItalic = false;

    @Column(name = "text_underline", nullable = false)
    private boolean textUnderline = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** When set, this block was inserted from a song/draft and stays linked for sync. */
    @Column(name = "source_document_id")
    private Integer sourceDocumentId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Transient
    public boolean isScene() {
        return TYPE_SCENE.equals(type);
    }

    public boolean isSceneDelimiter() {
        return sceneDelimiter;
    }

    public void setSceneDelimiter(boolean sceneDelimiter) {
        this.sceneDelimiter = sceneDelimiter;
    }

    public String getTextAlign() {
        return textAlign;
    }

    public void setTextAlign(String textAlign) {
        this.textAlign = textAlign;
    }

    public boolean isTextBold() {
        return textBold;
    }

    public void setTextBold(boolean textBold) {
        this.textBold = textBold;
    }

    public boolean isTextItalic() {
        return textItalic;
    }

    public void setTextItalic(boolean textItalic) {
        this.textItalic = textItalic;
    }

    public boolean isTextUnderline() {
        return textUnderline;
    }

    public void setTextUnderline(boolean textUnderline) {
        this.textUnderline = textUnderline;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Column(name = "tags")
    private String tags;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Integer sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }
}
