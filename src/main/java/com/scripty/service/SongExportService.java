package com.scripty.service;

import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import java.util.List;

/**
 * Downloads of a project's text documents, either one of them or every one of
 * a kind. Unlike the screenplay exporters, neither lyrics nor notes carry
 * element types, so the formats here are plain document formats rather than
 * screenplay formats — which is why the same renderer serves both: a note is a
 * title and its lines, exactly as a song is.
 *
 * <p>The one format that is not shared is MusicXML. That is a score rather
 * than a document, and a page of scene notes is not a thing to set to music.
 */
public interface SongExportService {

    enum Format {
        TXT("txt", "text/plain; charset=UTF-8"),
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        EPUB("epub", EpubPackage.CONTENT_TYPE),
        /**
         * The one format here that is not a document: a score, so lyrics can be
         * carried into MuseScore, Finale, Sibelius or Dorico and set to music.
         */
        MUSICXML(SongMusicXmlWriter.EXTENSION, SongMusicXmlWriter.CONTENT_TYPE);

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
            // Uncompressed only: a `.mxl` would need zipping, and every notation
            // program reads the plain form.
            case "musicxml", "xml" -> Format.MUSICXML;
            default -> Format.TXT;
        };
    }

    /** A rendered download: bytes plus the filename and content type to serve them with. */
    record SongExport(String filename, String contentType, byte[] content) {
    }

    /**
     * One song or note, rendered on its own.
     *
     * @return the rendered document, or null if it isn't found, isn't
     *         accessible, or is a note asked for as MusicXML
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
    default SongExport exportSongs(Integer projectId, List<Integer> songIds, Format format, User currentUser) {
        return exportDocuments(projectId, songIds, TextDocument.TYPE_SONG, format, currentUser);
    }

    /**
     * The project's documents of one kind as a single file, in list order —
     * the songbook, or the same thing made of notes.
     *
     * @param ids          the documents to include; null or empty means every
     *                     one of that kind. Ids outside this project are
     *                     ignored rather than trusted, so a tampered link
     *                     cannot pull in another project's writing.
     * @param documentType the kind to gather; null means songs, which is what
     *                     every caller meant before notes could be exported
     * @return the rendered documents, or null if the project isn't found or
     *         accessible, if ids was given but matched nothing here, or if
     *         notes were asked for as MusicXML
     */
    SongExport exportDocuments(Integer projectId, List<Integer> ids, String documentType,
                               Format format, User currentUser);
}
