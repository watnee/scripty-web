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
import java.time.LocalDateTime;

/**
 * A block's contents, kept after it was deleted so a writer can put it back.
 *
 * <p>This is a copy, not the original row. {@link Block} ids are not stable:
 * undo/redo and version restore delete an edition's blocks and re-insert them
 * with new ids, so marking the original row deleted would leave the trash
 * pointing at something that no longer exists. Copying the content instead —
 * the same thing a version snapshot does — keeps the trash independent of that
 * churn, and keeps {@code block} itself free of any deleted-row filtering.
 *
 * <p>A restore therefore produces a <em>new</em> block, not the old one. Nothing
 * else references a block by id, so only the content ever mattered.
 */
@Entity
@Table(name = "deleted_block")
public class DeletedBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "script_edition_id")
    private ScriptEdition scriptEdition;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_user_id")
    private User deletedBy;

    /**
     * Where the block sat when it was deleted. A hint only — the script keeps
     * being edited after a delete, so a restore clamps this to the edition's
     * current length rather than trusting it.
     */
    @Column(name = "original_order", nullable = false)
    private Integer originalOrder;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "`type`", nullable = false)
    private String type = Block.TYPE_ACTION;

    @Column(name = "scene_delimiter", nullable = false)
    private boolean sceneDelimiter;

    @Column(nullable = false)
    private boolean bookmarked;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "tags")
    private String tags;

    @Column(name = "text_align")
    private String textAlign;

    @Column(name = "font")
    private String font;

    @Column(name = "highlight")
    private String highlight;

    @Column(name = "text_bold", nullable = false)
    private boolean textBold;

    @Column(name = "text_italic", nullable = false)
    private boolean textItalic;

    @Column(name = "text_underline", nullable = false)
    private boolean textUnderline;

    /** Character cue kept by name; the person row may be gone by restore time. */
    @Column(name = "person_name", length = 60)
    private String personName;

    @Column(name = "source_document_id")
    private Integer sourceDocumentId;

    /** Copies everything from {@code block} that a restore needs to rebuild it. */
    public static DeletedBlock capture(Block block, User deletedBy, LocalDateTime deletedAt) {
        DeletedBlock record = new DeletedBlock();
        record.setProject(block.getProject());
        record.setScriptEdition(block.getScriptEdition());
        record.setDeletedAt(deletedAt);
        record.setDeletedBy(deletedBy);
        record.setOriginalOrder(block.getOrder());
        record.setContent(block.getContent());
        record.setType(block.getType());
        record.setSceneDelimiter(block.isSceneDelimiter());
        record.setBookmarked(block.isBookmarked());
        record.setPinned(block.isPinned());
        record.setTags(block.getTags());
        record.setTextAlign(block.getTextAlign());
        record.setFont(block.getFont());
        record.setHighlight(block.getHighlight());
        record.setTextBold(block.isTextBold());
        record.setTextItalic(block.isTextItalic());
        record.setTextUnderline(block.isTextUnderline());
        record.setSourceDocumentId(block.getSourceDocumentId());
        Person person = block.getPerson();
        record.setPersonName(person != null ? person.getName() : null);
        return record;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ScriptEdition getScriptEdition() {
        return scriptEdition;
    }

    public void setScriptEdition(ScriptEdition scriptEdition) {
        this.scriptEdition = scriptEdition;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public User getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(User deletedBy) {
        this.deletedBy = deletedBy;
    }

    public Integer getOriginalOrder() {
        return originalOrder;
    }

    public void setOriginalOrder(Integer originalOrder) {
        this.originalOrder = originalOrder;
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

    public boolean isSceneDelimiter() {
        return sceneDelimiter;
    }

    public void setSceneDelimiter(boolean sceneDelimiter) {
        this.sceneDelimiter = sceneDelimiter;
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

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getTextAlign() {
        return textAlign;
    }

    public void setTextAlign(String textAlign) {
        this.textAlign = textAlign;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
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

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public Integer getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(Integer sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }
}
