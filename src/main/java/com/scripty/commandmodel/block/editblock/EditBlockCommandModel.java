package com.scripty.commandmodel.block.editblock;

import jakarta.validation.constraints.NotNull;

public class EditBlockCommandModel {

    private Integer id;

    @NotNull(message = "You must supply a value for Content.")
    private String content;

    private Integer personId;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getPersonId() {
        return personId;
    }

    public void setPersonId(Integer personId) {
        this.personId = personId;
    }

    private String tags;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    /**
     * Formatting is optional on every edit: a null field leaves the stored value
     * alone. The web editor's block form never submits these, and API clients
     * auto-save content on a debounce without resending formatting.
     */
    private String textAlign;

    private String font;

    private Boolean textBold;

    private Boolean textItalic;

    private Boolean textUnderline;

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

    public Boolean getTextBold() {
        return textBold;
    }

    public void setTextBold(Boolean textBold) {
        this.textBold = textBold;
    }

    public Boolean getTextItalic() {
        return textItalic;
    }

    public void setTextItalic(Boolean textItalic) {
        this.textItalic = textItalic;
    }

    public Boolean getTextUnderline() {
        return textUnderline;
    }

    public void setTextUnderline(Boolean textUnderline) {
        this.textUnderline = textUnderline;
    }
}
