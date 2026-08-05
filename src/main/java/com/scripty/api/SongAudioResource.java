package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of one recording kept with a song.
 *
 * <p>Everything here describes the file; none of it is the file. The bytes are
 * behind the {@code audioFile} link, so a list of a song's takes costs the same
 * whether they are voice memos or full mixes, and nothing is transferred until
 * somebody presses play.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.AUDIO_RECORDING, collectionRelation = ApiRel.AUDIO_RECORDINGS)
public class SongAudioResource extends RepresentationModel<SongAudioResource> {

    private Integer id;
    private Integer documentId;
    /** What the writer calls this take, which is not always what the file is called. */
    private String title;
    private String fileName;
    private String contentType;
    private Long byteSize;
    /**
     * How long it plays, or absent when nobody could say — the uploader
     * measures it, since nothing on the server decodes audio. A client draws a
     * duration when it has one and leaves the space empty when it does not.
     */
    private Integer durationMs;
    private Integer sortOrder;
    private OffsetDateTime createdAt;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getByteSize() {
        return byteSize;
    }

    public void setByteSize(Long byteSize) {
        this.byteSize = byteSize;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
