package com.scripty.controller;

import com.scripty.api.ApiRel;
import com.scripty.api.RestErrors;
import com.scripty.api.TextDocumentResource;
import com.scripty.api.TextDocumentResourceAssembler;
import com.scripty.commandmodel.textdocument.TextDocumentCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.ScriptImportException;
import com.scripty.service.TextDocumentService;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST/HAL counterpart of {@link TextDocumentController}: manages a project's
 * songs and notes for the iPad client. Reuses {@link TextDocumentService}, so
 * behaviour (validation, insert-into-script sync, email sharing, import) stays
 * identical to the web app.
 */
@RestController
@RequestMapping(value = "/api/document")
public class TextDocumentRestController {

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    ProjectVersionService projectVersionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    TextDocumentResourceAssembler assembler;

    /** Songs and notes for a project. Optional {@code type} filters to SONG or NOTES. */
    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<TextDocumentResource>>> list(
            @RequestParam Integer projectId,
            @RequestParam(required = false) String type,
            Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TextDocumentListViewModel viewModel = textDocumentService.getListViewModel(projectId, currentUser(principal));
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        List<TextDocumentViewModel> documents = new ArrayList<>();
        String normalized = normalizeType(type);
        if (normalized == null || TextDocument.TYPE_SONG.equals(normalized)) {
            documents.addAll(viewModel.getSongs());
        }
        if (normalized == null || TextDocument.TYPE_NOTES.equals(normalized)) {
            documents.addAll(viewModel.getDrafts());
        }
        return ResponseEntity.ok(assembler.toCollection(documents, projectId, type));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<TextDocumentResource>> show(@PathVariable Integer id, Principal principal) {
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, currentUser(principal));
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(assembler.toModel(viewModel));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> create(
            @Valid @RequestBody TextDocumentCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        commandModel.setId(null);
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditScript(commandModel.getProjectId(), principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return saveAndRespond(commandModel, currentUser(principal), true);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody TextDocumentCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        commandModel.setId(id);
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        if (!projectAccess.canEditScript(commandModel.getProjectId(), principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return saveAndRespond(commandModel, currentUser(principal), false);
    }

    private ResponseEntity<?> saveAndRespond(TextDocumentCommandModel commandModel, User user, boolean isNew) {
        TextDocument saved = textDocumentService.save(commandModel, user);
        if (saved == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Editing an existing document keeps any script insertions in sync,
        // exactly as the web save does.
        if (!isNew && textDocumentService.syncInsertedBlocks(saved.getId(), user)) {
            projectVersionService.autoSaveVersion(commandModel.getProjectId());
        }
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(saved.getId(), user);
        EntityModel<TextDocumentResource> resource = assembler.toModel(viewModel);
        if (isNew) {
            return ResponseEntity
                    .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(resource);
        }
        return ResponseEntity.ok(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> delete(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer projectId,
            Principal principal) {
        User user = currentUser(principal);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        Integer resolvedProjectId = projectId != null ? projectId : viewModel.getProjectId();
        if (!projectAccess.canEditScript(resolvedProjectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        textDocumentService.delete(id, resolvedProjectId, user);
        return ResponseEntity.ok(assembler.toDeleteModel(resolvedProjectId));
    }

    /** Reassigns the sort order of a project's songs/notes to the supplied order. */
    @RequestMapping(value = "/reorder", method = RequestMethod.POST,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> reorder(
            @RequestParam Integer projectId,
            @RequestBody(required = false) ReorderRequest request,
            Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null || request.orderedIds() == null || request.orderedIds().isEmpty()) {
            return new ResponseEntity<>(
                    Map.of("orderedIds", "Provide the documents in their new order."),
                    HttpStatus.BAD_REQUEST);
        }
        List<TextDocument> reordered =
                textDocumentService.reorder(projectId, request.orderedIds(), currentUser(principal));
        if (reordered == null) {
            return new ResponseEntity<>(
                    Map.of("orderedIds", "Those documents do not all belong to this project."),
                    HttpStatus.BAD_REQUEST);
        }
        return list(projectId, null, principal);
    }

    /** Copies a song or note into a new document titled "… (copy)". */
    @RequestMapping(value = "/{id}/duplicate", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> duplicate(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer projectId,
            Principal principal) {
        User user = currentUser(principal);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        Integer resolvedProjectId = projectId != null ? projectId : viewModel.getProjectId();
        if (!projectAccess.canEditScript(resolvedProjectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TextDocument copy = textDocumentService.duplicate(id, resolvedProjectId, user);
        if (copy == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EntityModel<TextDocumentResource> resource =
                assembler.toModel(textDocumentService.getViewModel(copy.getId(), user));
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    /** Switches a document between song and note (SONG or NOTES). */
    @RequestMapping(value = "/{id}/change-type", method = RequestMethod.POST,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> changeType(
            @PathVariable Integer id,
            @RequestBody(required = false) ChangeTypeRequest request,
            @RequestParam(required = false) Integer projectId,
            Principal principal) {
        if (request == null || request.type() == null || request.type().isBlank()) {
            return new ResponseEntity<>(Map.of("type", "Choose SONG or NOTES."), HttpStatus.BAD_REQUEST);
        }
        User user = currentUser(principal);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        Integer resolvedProjectId = projectId != null ? projectId : viewModel.getProjectId();
        if (!projectAccess.canEditScript(resolvedProjectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        TextDocument changed =
                textDocumentService.changeType(id, resolvedProjectId, normalizeType(request.type()), user);
        if (changed == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(assembler.toModel(textDocumentService.getViewModel(changed.getId(), user)));
    }

    /** Inserts a document's content into the screenplay as blocks. */
    @RequestMapping(value = "/{id}/insert", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> insert(
            @PathVariable Integer id,
            @RequestBody(required = false) InsertDocumentRequest request,
            Principal principal) {
        User user = currentUser(principal);
        TextDocumentViewModel viewModel = textDocumentService.getViewModel(id, user);
        if (viewModel == null) {
            return ResponseEntity.notFound().build();
        }
        if (!projectAccess.canEditScript(viewModel.getProjectId(), principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Integer afterBlockId = request != null ? request.afterBlockId() : null;
        String asType = request != null ? request.asType() : null;
        List<Block> created = textDocumentService.insertIntoScript(id, afterBlockId, asType, user);
        if (!created.isEmpty()) {
            projectVersionService.autoSaveVersion(viewModel.getProjectId());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("inserted", created.size());
        response.put("projectId", viewModel.getProjectId());
        response.put("firstBlockId", created.isEmpty() ? null : created.get(0).getId());
        return ResponseEntity.ok(response);
    }

    /** Emails a song's lyrics to a recipient (songs only). */
    @RequestMapping(value = "/{id}/share-email", method = RequestMethod.POST,
            consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> shareEmail(
            @PathVariable Integer id,
            @RequestBody ShareEmailRequest request,
            Principal principal) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            return new ResponseEntity<>(Map.of("email", "You must supply a recipient address."),
                    HttpStatus.BAD_REQUEST);
        }
        List<TextDocument> shared = textDocumentService.shareSongsByEmail(
                List.of(id), request.email(), currentUser(principal));
        if (shared.isEmpty()) {
            return new ResponseEntity<>(
                    Map.of("email", "Could not email that song. Check the address and try again."),
                    HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(Map.of(
                "shared", true,
                "title", shared.get(0).getTitle(),
                "email", request.email().trim()));
    }

    /** Imports an uploaded file as a new song or note. */
    @RequestMapping(value = "/import", method = RequestMethod.POST, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> importFile(
            @RequestParam Integer projectId,
            @RequestParam(defaultValue = "SONG") String type,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = currentUser(principal);
        try {
            TextDocument saved = textDocumentService.importFile(projectId, type, file, user);
            if (saved == null) {
                return new ResponseEntity<>(
                        Map.of("file", "Could not import that file. Try a .txt, .fountain, .docx, .doc, .fdx, or .pdf file."),
                        HttpStatus.BAD_REQUEST);
            }
            TextDocumentViewModel viewModel = textDocumentService.getViewModel(saved.getId(), user);
            return ResponseEntity
                    .created(assembler.toModel(viewModel).getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(assembler.toModel(viewModel));
        } catch (ScriptImportException e) {
            return new ResponseEntity<>(Map.of("file", e.getUserMessage()), HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            return new ResponseEntity<>(
                    Map.of("file", "Could not import that file. Try a .txt, .fountain, .docx, .doc, .fdx, or .pdf file."),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if ("DRAFT".equalsIgnoreCase(type)
                || "DRAFTS".equalsIgnoreCase(type)
                || TextDocument.TYPE_NOTES.equalsIgnoreCase(type)
                || TextDocument.TYPE_OTHER.equalsIgnoreCase(type)) {
            return TextDocument.TYPE_NOTES;
        }
        return TextDocument.TYPE_SONG;
    }

    private User currentUser(Principal principal) {
        return projectAccess.currentUser(principal);
    }

    public record InsertDocumentRequest(Integer afterBlockId, String asType) {
    }

    public record ShareEmailRequest(String email) {
    }

    public record ReorderRequest(List<Integer> orderedIds) {
    }

    public record ChangeTypeRequest(String type) {
    }
}
