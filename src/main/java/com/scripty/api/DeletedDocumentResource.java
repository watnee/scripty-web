package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;

/**
 * HAL representation of a song or note sitting in the trash.
 *
 * <p>Carries a preview rather than the full text, for the same reason the
 * deleted-element resource does: this is a list you scan to find the thing you
 * deleted, and restoring brings it back whole either way. {@code purgesAt} says
 * how long that remains true.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeletedDocumentResource extends RepresentationModel<DeletedDocumentResource> {

    private Integer id;
    private String title;
    private String documentType;
    private String documentTypeLabel;
    private String preview;
    private OffsetDateTime deletedAt;
    private OffsetDateTime purgesAt;

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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public OffsetDateTime getPurgesAt() {
        return purgesAt;
    }

    public void setPurgesAt(OffsetDateTime purgesAt) {
        this.purgesAt = purgesAt;
    }
}
