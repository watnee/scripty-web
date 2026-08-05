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
 * A recording kept with a song — a voice memo of the melody, a demo, a
 * reference track. The words and the sound of them in one place.
 *
 * <p>Only the description of the file is here. The bytes live in
 * {@code song_audio_data} and are read by
 * {@link com.scripty.service.SongAudioService} with a query of its own, because
 * listing a song's recordings happens on every visit to the editor and playing
 * one happens when somebody presses play. Mapping the blob onto this entity
 * would collapse that distinction and pull megabytes through every list.
 *
 * <p>Songs only. A note is prose with nothing to hear, and the service refuses
 * an upload against one rather than leaving a recording somewhere no screen
 * would ever draw it.
 */
@Entity
@Table(name = "song_audio")
public class SongAudio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "text_document_id", nullable = false)
    private TextDocument textDocument;

    /**
     * What the writer calls this take. Seeded from the file name it arrived
     * under and renameable from then on — "Chorus idea, 2am" says something
     * {@code voice-memo-4.m4a} does not.
     */
    @Column(nullable = false, length = 200)
    private String title;

    /** The name it arrived under, so a download hands back something familiar. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    /**
     * How long it plays, as measured by whoever uploaded it, or null when they
     * could not say. Nothing on the server decodes audio; the browser and the
     * phone both have the file open in a player already, and asking them is
     * cheaper than teaching this application to read frame headers.
     */
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public TextDocument getTextDocument() {
        return textDocument;
    }

    public void setTextDocument(TextDocument textDocument) {
        this.textDocument = textDocument;
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

    public long getByteSize() {
        return byteSize;
    }

    public void setByteSize(long byteSize) {
        this.byteSize = byteSize;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
