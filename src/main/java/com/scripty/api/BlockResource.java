package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BlockResource extends RepresentationModel<BlockResource> {

    private Integer id;
    private Integer projectId;
    private Integer order;
    private String content;
    private String type;
    private Integer personId;
    private String personName;
    private Boolean bookmarked;
    private Boolean pinned;
    private Boolean scene;
    private String tags;
    private String textAlign;
    private String font;
    private String highlight;
    private Boolean textBold;
    private Boolean textItalic;
    private Boolean textUnderline;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProjectId() {
        return projectId;
    }

    public void setProjectId(Integer projectId) {
        this.projectId = projectId;
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

    public Integer getPersonId() {
        return personId;
    }

    public void setPersonId(Integer personId) {
        this.personId = personId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public Boolean getBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(Boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public Boolean getScene() {
        return scene;
    }

    public void setScene(Boolean scene) {
        this.scene = scene;
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
