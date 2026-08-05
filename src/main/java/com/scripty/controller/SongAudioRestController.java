package com.scripty.controller;

import com.scripty.api.RestErrors;
import com.scripty.api.SongAudioResource;
import com.scripty.api.SongAudioResourceAssembler;
import com.scripty.dto.SongAudio;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongAudioService;
import com.scripty.service.SongBlockService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A song's recordings over the API, and over the browser too — unlike most of
 * this application's surfaces there is no MVC twin. The web page reaches these
 * same endpoints with {@code fetch} and an {@code <audio>} element, because
 * what they answer with is a list of JSON and a stream of bytes rather than a
 * page, and a second controller rendering the same two things in Thymeleaf
 * would only be a second place for them to drift.
 *
 * <p>Reads need access to the project; uploading, renaming and deleting need
 * the same edit permission lyrics do. Everything is scoped by {@code documentId}
 * as well as by the recording's own id, so a recording is only ever reachable
 * through the song that holds it — an id guessed from elsewhere resolves to
 * nothing.
 */
@RestController
@RequestMapping("/api/song/audio")
public class SongAudioRestController {

    @Autowired
    SongAudioService songAudioService;

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    SongAudioResourceAssembler assembler;

    private boolean canAccessDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canEditDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    /** Every recording kept with this song, in the order they are shown. */
    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<SongAudioResource>>> list(
            @RequestParam Integer documentId, Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(assembler.toCollection(
                songAudioService.listForDocument(documentId), documentId));
    }

    /**
     * Adds a recording to this song.
     *
     * <p>The service does the validating, so the web form and this endpoint
     * refuse the same files for the same reasons, and its refusal is already a
     * sentence fit to show a writer.
     */
    @RequestMapping(method = RequestMethod.POST, consumes = "multipart/form-data",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> upload(
            @RequestParam Integer documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer durationMs,
            Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SongAudio stored;
        try {
            stored = songAudioService.store(documentId, file, title, durationMs);
        } catch (IllegalArgumentException ex) {
            return new ResponseEntity<>(RestErrors.of("file", ex.getMessage()), HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toModel(stored, documentId));
    }

    /** One recording's description — the same resource the list carries. */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> show(@PathVariable Integer id,
                                  @RequestParam Integer documentId,
                                  Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return songAudioService.find(documentId, id)
                .<ResponseEntity<?>>map(audio -> ResponseEntity.ok(assembler.toModel(audio, documentId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The recording itself.
     *
     * <p>No {@code produces} on purpose: the type is whatever was uploaded, and
     * declaring one would have this refuse the {@code Accept: application/hal+json}
     * every other request from the native client sends. {@code inline} rather
     * than {@code attachment} because the usual thing to do with it is press
     * play; a browser saving it still gets the file's own name.
     *
     * <p>Byte ranges are handled by Spring's resource support, which is what
     * lets a player seek without fetching the whole take again.
     */
    @RequestMapping(value = "/{id}/file", method = RequestMethod.GET)
    public ResponseEntity<Resource> file(@PathVariable Integer id,
                                         @RequestParam Integer documentId,
                                         Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SongAudio audio = songAudioService.find(documentId, id).orElse(null);
        Resource data = audio == null ? null : songAudioService.loadData(documentId, id).orElse(null);
        if (audio == null || data == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(audio.getContentType());
        } catch (RuntimeException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(audio.getFileName()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(data);
    }

    /** Renames a recording. The file is untouched; only what it is called changes. */
    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> rename(@PathVariable Integer id,
                                    @RequestParam Integer documentId,
                                    @RequestBody(required = false) RenameSongAudioRequest request,
                                    Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String title = request != null ? request.title() : null;
        if (title == null || title.isBlank()) {
            return new ResponseEntity<>(RestErrors.of("title", "Give this recording a name."),
                    HttpStatus.BAD_REQUEST);
        }
        return songAudioService.rename(documentId, id, title)
                .<ResponseEntity<?>>map(audio -> ResponseEntity.ok(assembler.toModel(audio, documentId)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Deletes a recording, bytes and all. Unlike a deleted lyric line this does
     * not go to the trash — there is nowhere in this application that keeps a
     * file after it is thrown away, and pretending otherwise would be a promise
     * nothing keeps. The client asks first.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> delete(@PathVariable Integer id,
                                    @RequestParam Integer documentId,
                                    Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!songAudioService.delete(documentId, id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toCollection(
                songAudioService.listForDocument(documentId), documentId));
    }

    /**
     * A disposition header a file name cannot break out of: the plain form for
     * anything ASCII, and RFC 5987's encoded form beside it so a take named in
     * Japanese survives the trip.
     */
    private String contentDisposition(String fileName) {
        String safe = fileName == null ? "recording" : fileName.replaceAll("[\"\\\\\\r\\n]", "");
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }

    /** The new name for a recording. */
    public record RenameSongAudioRequest(String title) {
    }
}
