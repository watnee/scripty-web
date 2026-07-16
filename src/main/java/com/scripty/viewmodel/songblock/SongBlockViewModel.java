package com.scripty.viewmodel.songblock;

public class SongBlockViewModel {

    private Integer id;
    private Integer documentId;
    private Integer order;
    private String content;
    private String highlight;

    public SongBlockViewModel() {
    }

    public SongBlockViewModel(Integer id, Integer documentId, Integer order, String content, String highlight) {
        this.id = id;
        this.documentId = documentId;
        this.order = order;
        this.content = content;
        this.highlight = highlight;
    }

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
