package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of an archived song or note.
 *
 * <p>Shaped like {@link DeletedDocumentResource} — a preview rather than the
 * full text, for a list you scan — but with no purge date, because nothing
 * expires out of the archive. {@code archivedAt} is only there to sort by and
 * to tell the writer when they put the piece down.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.ARCHIVED_DOCUMENT, collectionRelation = ApiRel.ARCHIVED_DOCUMENTS)
public class ArchivedDocumentResource extends RepresentationModel<ArchivedDocumentResource> {

    private Integer id;
    private String title;
    private String documentType;
    private String documentTypeLabel;
    private String preview;
    private OffsetDateTime archivedAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentTypeLabel() {
        return documentTypeLabel;
    }

    public void setDocumentTypeLabel(String documentTypeLabel) {
        this.documentTypeLabel = documentTypeLabel;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
