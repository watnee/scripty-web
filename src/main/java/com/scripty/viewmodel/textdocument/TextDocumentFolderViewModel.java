package com.scripty.viewmodel.textdocument;

import java.util.ArrayList;
import java.util.List;

/**
 * One folder as a list page draws it: a heading, and the documents under it.
 *
 * <p>Carries its documents rather than leaving the page to filter the flat list
 * by folder id, so a template renders a folder by walking one collection. The
 * flat lists on {@link TextDocumentListViewModel} still hold every document,
 * filed or not — the exports, the counts and the REST listing all want the
 * whole list and none of them care where anything sits.
 */
public class TextDocumentFolderViewModel {

    private Integer id;
    private String name;
    /** SONG or NOTES — which list this folder belongs to. */
    private String documentType;
    private List<TextDocumentViewModel> documents = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public List<TextDocumentViewModel> getDocuments() {
        return documents;
    }

    public void setDocuments(List<TextDocumentViewModel> documents) {
        this.documents = documents != null ? documents : new ArrayList<>();
    }

    public int getDocumentCount() {
        return documents.size();
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }
}
