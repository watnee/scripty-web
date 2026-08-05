package com.scripty.service;

import com.scripty.dto.SongAudio;
import java.util.List;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recordings kept with a song: uploading one, listing them, playing one back,
 * renaming and deleting.
 *
 * <p>Every method that names a document takes the document id, and every one of
 * them checks that the document is a song that still exists before it does
 * anything. Access is the caller's business — the controllers ask
 * {@link com.scripty.security.ProjectAccessSupport} the same questions they ask
 * for lyrics — but the shape of what may hold a recording is decided here, in
 * one place, so the web form and the API cannot disagree about it.
 */
public interface SongAudioService {

    /** A song's recordings, in the order they are shown. */
    List<SongAudio> listForDocument(Integer documentId);

    /** One recording, only if it belongs to the named song. */
    Optional<SongAudio> find(Integer documentId, Integer audioId);

    /**
     * Stores an uploaded file against a song.
     *
     * @param title      what to call it, or null/blank to name it after the file
     * @param durationMs how long it plays, as the uploader measured it, or null
     * @throws IllegalArgumentException with a sentence fit to show a writer when
     *                                  the file is too big, is not audio, or the
     *                                  document is not a song
     */
    SongAudio store(Integer documentId, MultipartFile file, String title, Integer durationMs);

    /** Renames a recording, and answers empty if it is not this song's. */
    Optional<SongAudio> rename(Integer documentId, Integer audioId, String title);

    /** Deletes a recording. Answers whether there was one to delete. */
    boolean delete(Integer documentId, Integer audioId);

    /** The bytes, for playing or downloading. */
    Optional<Resource> loadData(Integer documentId, Integer audioId);

    /** The cap an upload is measured against, in bytes — for the message a form shows. */
    long maxBytes();
}
