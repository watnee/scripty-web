package com.scripty.controller;

import com.scripty.api.SongVersionResource;
import com.scripty.api.SongVersionResourceAssembler;
import com.scripty.dto.SongEdition;
import com.scripty.dto.SongVersion;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongEditionService;
import com.scripty.service.SongVersionService;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import com.scripty.viewmodel.song.versionhistory.SongVersionViewModel;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST/HAL counterpart of {@link SongVersionController}, mirroring
 * {@link ProjectVersionRestController} for songs. Reuses
 * {@link SongVersionService} so behaviour (snapshotting, restore, auto-save
 * coalescing) stays identical to the web app. Reads require project access;
 * create/restore/delete require script edit permission, mirroring the web
 * controller.
 */
@RestController
@RequestMapping(value = "/api/song/version")
public class SongVersionRestController {

    @Autowired
    SongVersionService songVersionService;

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    SongEditionService songEditionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    SongVersionResourceAssembler assembler;

    private boolean canAccessDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canEditDocument(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    /** Resolves a missing editionId to the song's default version (back-compat). */
    private Integer effectiveEdition(Integer documentId, Integer editionId) {
        SongEdition edition = songEditionService.requireForDocument(documentId, editionId);
        if (edition == null) {
            edition = songEditionService.ensureDefaultEdition(documentId);
        }
        return edition != null ? edition.getId() : editionId;
    }

    /** Saved versions for a song, newest first. */
    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<SongVersionResource>>> list(
            @RequestParam Integer documentId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SongVersionHistoryViewModel viewModel = songVersionService.getVersionHistoryViewModel(documentId, editionId);
        return ResponseEntity.ok(assembler.toCollection(viewModel));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<SongVersionResource>> show(
            @PathVariable Integer id,
            @RequestParam Integer documentId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!canAccessDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        SongVersionHistoryViewModel viewModel = songVersionService.getVersionHistoryViewModel(documentId, editionId);
        SongVersionViewModel version = findVersion(viewModel, id);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toModel(version, viewModel.getDocumentId(), viewModel.getProjectId()));
    }

    /** Captures the song's current lyrics as a new named version. */
    @RequestMapping(method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestParam Integer documentId,
            @RequestParam(required = false) Integer editionId,
            @RequestBody(required = false) CreateSongVersionRequest request,
            Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String label = request != null ? request.label() : null;
        if (label == null || label.isBlank()) {
            label = "Version";
        }
        SongVersion created = songVersionService.createVersion(documentId, editionId, label);
        SongVersionHistoryViewModel viewModel = songVersionService.getVersionHistoryViewModel(documentId, editionId);
        SongVersionViewModel version = created != null ? findVersion(viewModel, created.getId()) : null;
        if (version == null) {
            // Should not happen, but never fail the create over a read-back miss.
            return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toCollection(viewModel));
        }
        EntityModel<SongVersionResource> resource =
                assembler.toModel(version, viewModel.getDocumentId(), viewModel.getProjectId());
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    /** Restores the song to a saved version, then returns the refreshed history. */
    @RequestMapping(value = "/{id}/restore", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> restore(
            @PathVariable Integer id,
            @RequestParam Integer documentId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        if (!songVersionService.restoreVersionForDocument(id, resolved)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toCollection(
                songVersionService.getVersionHistoryViewModel(documentId, resolved)));
    }

    /** Deletes a saved version, then returns the refreshed history. */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> delete(
            @PathVariable Integer id,
            @RequestParam Integer documentId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!canEditDocument(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer resolved = effectiveEdition(documentId, editionId);
        if (!songVersionService.deleteVersionForDocument(id, resolved)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toCollection(
                songVersionService.getVersionHistoryViewModel(documentId, resolved)));
    }

    private SongVersionViewModel findVersion(SongVersionHistoryViewModel viewModel, Integer versionId) {
        if (viewModel.getVersions() == null || versionId == null) {
            return null;
        }
        for (SongVersionViewModel version : viewModel.getVersions()) {
            if (versionId.equals(version.getId())) {
                return version;
            }
        }
        return null;
    }

    public record CreateSongVersionRequest(String label) {
    }
}
