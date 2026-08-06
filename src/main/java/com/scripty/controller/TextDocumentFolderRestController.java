package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.TextDocumentFolderResource;
import com.scripty.dto.TextDocument;
import com.scripty.dto.TextDocumentFolder;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.DocumentFolderException;
import com.scripty.service.TextDocumentFolderService;
import com.scripty.service.TextDocumentService;
import com.scripty.viewmodel.textdocument.TextDocumentListViewModel;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Folders: the headings a project's songs or notes are filed under.
 *
 * <p>Scoped by {@code type} throughout, because a folder belongs to one list.
 * A client showing Songs asks for {@code type=SONG} and is never handed a
 * folder it could not put anything in.
 *
 * <p>Mapped at {@code /api/document/folder}, a literal sibling of
 * {@code /api/document/{id}} — the same shape {@code /api/document/archive}
 * already has, and resolved the same way: a literal segment beats a path
 * variable.
 *
 * <p>Every write answers with the refreshed folder collection rather than the
 * one folder, since renaming or removing one changes what the whole list looks
 * like. Moving a *document* between folders is not here at all: that is a write
 * to the document, and lives on {@link TextDocumentRestController} beside the
 * document's other affordances.
 */
@RestController
@RequestMapping("/api/document/folder")
public class TextDocumentFolderRestController {

    @Autowired
    TextDocumentFolderService folderService;

    @Autowired
    TextDocumentService textDocumentService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@RequestParam Integer projectId,
                                  @RequestParam(required = false) String type,
                                  Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        CollectionModel<EntityModel<TextDocumentFolderResource>> collection =
                collection(projectId, type, principal);
        if (collection == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection);
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> create(@RequestParam Integer projectId,
                                    @RequestParam(required = false) String type,
                                    @RequestBody(required = false) FolderNameRequest request,
                                    Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String name = request != null ? request.name() : null;
        try {
            TextDocumentFolder created = folderService.create(
                    projectId, normalizeType(type), name, projectAccess.currentUser(principal));
            if (created == null) {
                return ResponseEntity.notFound().build();
            }
        } catch (DocumentFolderException e) {
            return new ResponseEntity<>(Map.of("name", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(collection(projectId, type, principal));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> rename(@PathVariable Integer id,
                                    @RequestParam Integer projectId,
                                    @RequestParam(required = false) String type,
                                    @RequestBody(required = false) FolderNameRequest request,
                                    Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String name = request != null ? request.name() : null;
        try {
            if (folderService.rename(id, projectId, name, projectAccess.currentUser(principal)) == null) {
                return ResponseEntity.notFound().build();
            }
        } catch (DocumentFolderException e) {
            return new ResponseEntity<>(Map.of("name", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(collection(projectId, type, principal));
    }

    /**
     * Removes a folder. Nothing filed under it is deleted — those documents
     * stay in the list, unfiled — so this needs no confirmation ceremony of its
     * own and is not a soft delete.
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE,
            produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> delete(@PathVariable Integer id,
                                    @RequestParam Integer projectId,
                                    @RequestParam(required = false) String type,
                                    Principal principal) {
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (folderService.delete(id, projectId, projectAccess.currentUser(principal)) < 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(projectId, type, principal));
    }

    /**
     * One list's folders, each carrying how many documents are filed under it —
     * or both lists' when no {@code type} was asked for, which is the same
     * rule the document listing follows.
     *
     * <p>The counts come from the document list rather than from a count query
     * per folder: the caller is holding that list anyway, and one pass over it
     * is cheaper than a query per heading.
     */
    private CollectionModel<EntityModel<TextDocumentFolderResource>> collection(
            Integer projectId, String type, Principal principal) {
        User user = projectAccess.currentUser(principal);
        // Passed on verbatim to every link below, so an answer stays in the
        // scope the caller was looking at: ask for both lists and the links
        // come back asking for both.
        String listType = listType(type);
        List<TextDocumentFolder> folders = listType == null
                ? folderService.listAll(projectId, user)
                : folderService.list(projectId, listType, user);
        if (folders == null) {
            return null;
        }
        Map<Integer, Integer> counts = documentCounts(projectId, user);
        boolean canEdit = projectAccess.canEditScriptForCurrentUser(projectId);

        List<EntityModel<TextDocumentFolderResource>> resources = new ArrayList<>();
        for (TextDocumentFolder folder : folders) {
            TextDocumentFolderResource resource = new TextDocumentFolderResource();
            resource.setId(folder.getId());
            resource.setProjectId(projectId);
            resource.setDocumentType(folder.getDocumentType());
            resource.setName(folder.getName());
            resource.setDocumentCount(counts.getOrDefault(folder.getId(), 0));
            resource.setCreatedAt(ApiDates.toOffset(folder.getCreatedAt()));
            resource.setUpdatedAt(ApiDates.toOffset(folder.getUpdatedAt()));
            resources.add(EntityModel.of(resource, itemLinks(folder, projectId, listType, canEdit)));
        }

        CollectionModel<EntityModel<TextDocumentFolderResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(TextDocumentFolderRestController.class)
                        .list(projectId, listType, null)).withSelfRel())
                .add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, listType, null))
                        .withRel(ApiRel.DOCUMENTS))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
        if (canEdit) {
            // Unconditional, unlike the bulk rels on the document collection: a
            // client needs somewhere to send the *first* folder, and an empty
            // list is exactly when it needs it most.
            collection.add(linkTo(methodOn(TextDocumentFolderRestController.class)
                    .create(projectId, listType, null, null)).withRel(ApiRel.CREATE_FOLDER));
        }
        return collection;
    }

    private Link[] itemLinks(TextDocumentFolder folder, Integer projectId, String listType, boolean canEdit) {
        List<Link> links = new ArrayList<>();
        links.add(linkTo(methodOn(TextDocumentFolderRestController.class)
                .list(projectId, listType, null)).withRel(ApiRel.FOLDERS));
        // What is in this folder, for a client that wants only these rows —
        // the same document listing, narrowed. Outside the edit gate: reading a
        // folder's contents is a read.
        links.add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, listType, null))
                .withRel(ApiRel.DOCUMENTS));
        if (canEdit) {
            links.add(linkTo(methodOn(TextDocumentFolderRestController.class)
                    .rename(folder.getId(), projectId, listType, null, null))
                    .withRel(ApiRel.RENAME_FOLDER));
            links.add(linkTo(methodOn(TextDocumentFolderRestController.class)
                    .delete(folder.getId(), projectId, listType, null))
                    .withRel(ApiRel.DELETE_FOLDER));
        }
        return links.toArray(Link[]::new);
    }

    /** How many of the project's listed documents sit in each folder. */
    private Map<Integer, Integer> documentCounts(Integer projectId, User user) {
        Map<Integer, Integer> counts = new HashMap<>();
        TextDocumentListViewModel list = textDocumentService.getListViewModel(projectId, user);
        if (list == null) {
            return counts;
        }
        countInto(counts, list.getSongs());
        countInto(counts, list.getDrafts());
        return counts;
    }

    private void countInto(Map<Integer, Integer> counts, List<TextDocumentViewModel> documents) {
        for (TextDocumentViewModel document : documents) {
            if (document.getFolderId() != null) {
                counts.merge(document.getFolderId(), 1, Integer::sum);
            }
        }
    }

    /**
     * SONG, NOTES, or null for "both lists".
     *
     * <p>Only the listing takes null — a folder has to be made in one list or
     * the other, so {@link #normalizeType} is what the writes use.
     */
    private static String listType(String type) {
        return type == null || type.isBlank() ? null : normalizeType(type);
    }

    /**
     * SONG or NOTES, by the same rule the web list routes use: the drafts
     * spellings and the legacy OTHER all mean the notes list, and a missing
     * type means Songs — which is the list a write with no type lands in.
     */
    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return TextDocument.TYPE_SONG;
        }
        if ("DRAFT".equalsIgnoreCase(type)
                || "DRAFTS".equalsIgnoreCase(type)
                || TextDocument.TYPE_NOTES.equalsIgnoreCase(type)
                || TextDocument.TYPE_OTHER.equalsIgnoreCase(type)) {
            return TextDocument.TYPE_NOTES;
        }
        return TextDocument.TYPE_SONG;
    }

    public record FolderNameRequest(String name) {
    }
}
