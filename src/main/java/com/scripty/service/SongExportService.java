package com.scripty.service;

import com.scripty.dto.User;
import java.util.List;

/**
 * Downloads of song lyrics, either one song or every song in a project.
 * Unlike the screenplay exporters, lyrics carry no element types, so the
 * formats here are plain document formats rather than screenplay formats.
 */
public interface SongExportService {

    enum Format {
        TXT("txt", "text/plain; charset=UTF-8"),
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        EPUB("epub", EpubPackage.CONTENT_TYPE);

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
            case "epub" -> Format.EPUB;
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
    default SongExport exportAllSongs(Integer projectId, Format format, User currentUser) {
        return exportSongs(projectId, null, format, currentUser);
    }

    /**
     * The project's songs as one document, in list order.
     *
     * @param songIds the songs to include; null or empty means every song. Ids
     *                outside this project are ignored rather than trusted, so a
     *                tampered link cannot pull in another project's lyrics.
     * @return the rendered songs, or null if the project isn't found or
     *         accessible, or if songIds was given but matched no song here
     */
    SongExport exportSongs(Integer projectId, List<Integer> songIds, Format format, User currentUser);
}
