package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;

/**
 * HAL representation of one ordered lyric line of a song. The web app edits
 * these under the song editor; this is the REST counterpart the iPad client
 * follows from a song document's {@code songBlocks} link.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SongBlockResource extends RepresentationModel<SongBlockResource> {

    private Integer id;
    private Integer documentId;
    private Integer order;
    private String content;
    private String highlight;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Integer documentId) {
        this.documentId = documentId;
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

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
    }
}
