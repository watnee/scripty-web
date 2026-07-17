package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.hateoas.RepresentationModel;

/**
 * HAL representation of one lyric line of a song, the song counterpart of
 * {@link BlockResource}. A song block carries only order, text and highlight —
 * songs have no element types, characters or scene flags.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SongBlockResource extends RepresentationModel<SongBlockResource> {

    private Integer id;
    private Integer documentId;
    private Integer projectId;
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

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
    }
}
