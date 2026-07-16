package com.scripty.service;

import com.scripty.dto.User;

/**
 * Downloads of song lyrics, either one song or every song in a project.
 * Unlike the screenplay exporters, lyrics carry no element types, so the
 * formats here are plain document formats rather than screenplay formats.
 */
public interface SongExportService {

    enum Format {
        TXT("txt", "text/plain; charset=UTF-8"),
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    /** Falls back to TXT for anything unrecognized, so a stale link still downloads something. */
    static Format parseFormat(String raw) {
        if (raw == null) {
            return Format.TXT;
        }
        return switch (raw.trim().toLowerCase()) {
            case "pdf" -> Format.PDF;
            case "docx", "word" -> Format.DOCX;
            default -> Format.TXT;
        };
    }

    /** A rendered download: bytes plus the filename and content type to serve them with. */
    record SongExport(String filename, String contentType, byte[] content) {
    }

    /**
     * @return the rendered song, or null if it isn't found, isn't accessible, or isn't a song
     */
    SongExport exportSong(Integer documentId, Format format, User currentUser);

    /**
     * Every song in the project, in list order, as one document.
     * @return the rendered songs, or null if the project isn't found or accessible
     */
    SongExport exportAllSongs(Integer projectId, Format format, User currentUser);
}
