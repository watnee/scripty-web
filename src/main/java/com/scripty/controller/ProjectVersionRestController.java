package com.scripty.controller;

import com.scripty.api.ProjectVersionResource;
import com.scripty.api.ProjectVersionResourceAssembler;
import com.scripty.dto.ProjectVersion;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectVersionService;
import com.scripty.viewmodel.project.versionhistory.VersionHistoryViewModel;
import com.scripty.viewmodel.project.versionhistory.VersionViewModel;
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
 * REST/HAL counterpart of {@link ProjectVersionController}: exposes a project's
 * version history to native clients. Reuses {@link ProjectVersionService} so
 * behaviour (snapshotting, restore, auto-save pruning) stays identical to the
 * web app. Reads require project access; create/restore/delete require script
 * edit permission, mirroring the web controller.
 */
@RestController
@RequestMapping(value = "/api/project/version")
public class ProjectVersionRestController {

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    ProjectVersionResourceAssembler assembler;

    /** Saved versions for a project, newest first. Optional {@code editionId} scopes to one script edition. */
    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<ProjectVersionResource>>> list(
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        VersionHistoryViewModel viewModel = projectVersionService.getVersionHistoryViewModel(projectId, editionId);
        return ResponseEntity.ok(assembler.toCollection(viewModel, editionId));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<ProjectVersionResource>> show(
            @PathVariable Integer id,
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        VersionViewModel version = findVersion(projectId, editionId, id);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toModel(version, projectId, editionId));
    }

    /** Captures the current script state as a new named version. */
    @RequestMapping(method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            @RequestBody(required = false) CreateVersionRequest request,
            Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String label = request != null ? request.label() : null;
        if (label == null || label.isBlank()) {
            label = "Version";
        }
        ProjectVersion created = projectVersionService.createVersion(projectId, editionId, label);
        VersionViewModel version = findVersion(projectId, editionId, created.getId());
        if (version == null) {
            // Should not happen, but never fail the create over a read-back miss.
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(assembler.toCollection(
                            projectVersionService.getVersionHistoryViewModel(projectId, editionId), editionId));
        }
        EntityModel<ProjectVersionResource> resource = assembler.toModel(version, projectId, editionId);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    /** Restores the project to a saved version, then returns the refreshed history. */
    @RequestMapping(value = "/{id}/restore", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> restore(
            @PathVariable Integer id,
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!projectVersionService.restoreVersionForProject(id, projectId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                assembler.toCollection(projectVersionService.getVersionHistoryViewModel(projectId, editionId), editionId));
    }

    /** Deletes a saved version, then returns the refreshed history. */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> delete(
            @PathVariable Integer id,
            @RequestParam Integer projectId,
            @RequestParam(required = false) Integer editionId,
            Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!projectVersionService.deleteVersionForProject(id, projectId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(
                assembler.toCollection(projectVersionService.getVersionHistoryViewModel(projectId, editionId), editionId));
    }

    private VersionViewModel findVersion(Integer projectId, Integer editionId, Integer versionId) {
        VersionHistoryViewModel viewModel = projectVersionService.getVersionHistoryViewModel(projectId, editionId);
        if (viewModel.getVersions() == null) {
            return null;
        }
        for (VersionViewModel version : viewModel.getVersions()) {
            if (versionId != null && versionId.equals(version.getId())) {
                return version;
            }
        }
        return null;
    }

    public record CreateVersionRequest(String label) {
    }
}
