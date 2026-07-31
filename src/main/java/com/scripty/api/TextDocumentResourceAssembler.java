package com.scripty.api;

import com.scripty.controller.ProjectRestController;
import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.SongEditionRestController;
import com.scripty.controller.SongVersionRestController;
import com.scripty.controller.DocumentArchiveRestController;
import com.scripty.controller.DocumentTrashRestController;
import com.scripty.controller.TextDocumentController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.dto.TextDocument;
import com.scripty.viewmodel.textdocument.TextDocumentViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import com.scripty.security.ProjectAccessSupport;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Builds HAL resources for project text documents (songs and notes). Mutation
 * links (update, delete, insert, share, import) appear only when the current
 * user can edit the script — the client gates its UI on their presence, the
 * same rule the block editor uses.
 */
@Component
public class TextDocumentResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

    public EntityModel<TextDocumentResource> toModel(TextDocumentViewModel document) {
        return EntityModel.of(toResource(document, true))
                .add(documentLinks(document.getId(), document.getProjectId(),
                        document.getDocumentType()));
    }

    public EntityModel<TextDocumentResource> toDeleteModel(Integer projectId) {
        TextDocumentResource resource = new TextDocumentResource();
        resource.setProjectId(projectId);
        return EntityModel.of(resource,
                linkTo(methodOn(TextDocumentRestController.class).list(projectId, null, null))
                        .withRel(ApiRel.DOCUMENTS));
    }

    /** All documents (songs and notes) for one project. */
    public CollectionModel<EntityModel<TextDocumentResource>> toCollection(
            List<TextDocumentViewModel> documents, Integer projectId, String type) {
        List<EntityModel<TextDocumentResource>> resources = new ArrayList<>();
        for (TextDocumentViewModel document : documents) {
            resources.add(toSummaryModel(document));
        }
        Link self = linkTo(methodOn(TextDocumentRestController.class).list(projectId, type, null))
                .withSelfRel();
        if (canEdit(projectId)) {
            self = self.andAffordance(afford(methodOn(TextDocumentRestController.class)
                    .importFile(null, null, null, null)));
        }
        CollectionModel<EntityModel<TextDocumentResource>> collection = CollectionModel.of(resources)
                .add(self)
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
        // A songbook of the whole project, and the same gathering made of
        // notes — each offered only where there is something to put in it.
        // Exporting is a read, so these sit outside the edit gate.
        boolean hasSong = false;
        boolean hasNote = false;
        for (TextDocumentViewModel document : documents) {
            if (TextDocument.TYPE_SONG.equalsIgnoreCase(document.getDocumentType())) {
                hasSong = true;
            } else {
                hasNote = true;
            }
        }
        if (hasSong) {
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "txt", null, null, null)).withRel(ApiRel.EXPORT_SONGS_TXT));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "pdf", null, null, null)).withRel(ApiRel.EXPORT_SONGS_PDF));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "docx", null, null, null)).withRel(ApiRel.EXPORT_SONGS_DOCX));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "epub", null, null, null)).withRel(ApiRel.EXPORT_SONGS_EPUB));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "musicxml", null, null, null))
                    .withRel(ApiRel.EXPORT_SONGS_MUSICXML));
        }
        if (hasNote) {
            // The same four document formats, with `type` naming the other
            // list. No MusicXML: the endpoint refuses notes a score.
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "txt", null, TextDocument.TYPE_NOTES, null))
                    .withRel(ApiRel.EXPORT_NOTES_TXT));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "pdf", null, TextDocument.TYPE_NOTES, null))
                    .withRel(ApiRel.EXPORT_NOTES_PDF));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "docx", null, TextDocument.TYPE_NOTES, null))
                    .withRel(ApiRel.EXPORT_NOTES_DOCX));
            collection.add(linkTo(methodOn(TextDocumentController.class)
                    .exportSongs(projectId, "epub", null, TextDocument.TYPE_NOTES, null))
                    .withRel(ApiRel.EXPORT_NOTES_EPUB));
        }
        if (!documents.isEmpty() && canEdit(projectId)) {
            // Deleting and emailing a selection used to be songs-only, because
            // the services behind them skipped anything that was not a song.
            // They no longer do: a selection of notes deletes and emails
            // exactly as a selection of songs does, so the only question left
            // is whether there is anything at all to select.
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .bulkDelete(projectId, null, null)).withRel(ApiRel.BULK_DELETE));
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .bulkShareEmail(projectId, null, null)).withRel(ApiRel.BULK_SHARE_EMAIL));
        }
        if (canEdit(projectId)) {
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .importFile(null, null, null, null)).withRel(ApiRel.IMPORT_DOCUMENT));
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .reorder(projectId, null, null)).withRel(ApiRel.REORDER));
            // Deleting a song or note is recoverable; say where it went.
            collection.add(linkTo(methodOn(DocumentTrashRestController.class)
                    .list(projectId, null)).withRel(ApiRel.TRASH));
            // The archive is always reachable, empty or not — unlike the bulk
            // rels below it needs no song to be useful, since notes archive too,
            // and a client wants somewhere to send the first one.
            collection.add(linkTo(methodOn(DocumentArchiveRestController.class)
                    .list(projectId, null)).withRel(ApiRel.ARCHIVED));
            collection.add(linkTo(methodOn(TextDocumentRestController.class)
                    .bulkArchive(projectId, null, null)).withRel(ApiRel.BULK_ARCHIVE));
        }
        return collection;
    }

    /** Summary form used in the list: preview instead of full content. */
    private EntityModel<TextDocumentResource> toSummaryModel(TextDocumentViewModel document) {
        return EntityModel.of(toResource(document, false))
                .add(documentLinks(document.getId(), document.getProjectId(),
                        document.getDocumentType()));
    }

    private TextDocumentResource toResource(TextDocumentViewModel document, boolean includeContent) {
        TextDocumentResource resource = new TextDocumentResource();
        resource.setId(document.getId());
        resource.setProjectId(document.getProjectId());
        resource.setProjectTitle(document.getProjectTitle());
        resource.setTitle(document.getTitle());
        resource.setDocumentType(document.getDocumentType());
        resource.setDocumentTypeLabel(document.getDocumentTypeLabel());
        resource.setPreview(document.getPreview());
        resource.setSortOrder(document.getSortOrder());
        resource.setCreatedAt(ApiDates.toOffset(document.getCreatedAt()));
        resource.setUpdatedAt(ApiDates.toOffset(document.getUpdatedAt()));
        if (includeContent) {
            resource.setContent(document.getContent());
        }
        return resource;
    }

    private Link[] documentLinks(int id, Integer projectId, String type) {
        List<Link> links = new ArrayList<>();
        Link self = linkTo(methodOn(TextDocumentRestController.class).show(id, null)).withSelfRel();
        if (canEdit(projectId)) {
            self = self
                    .andAffordance(afford(methodOn(TextDocumentRestController.class).update(id, null, null, null)))
                    .andAffordance(afford(methodOn(TextDocumentRestController.class).delete(id, null, null)));
        }
        links.add(self);
        if (TextDocument.TYPE_SONG.equalsIgnoreCase(type)) {
            // Only songs are edited as ordered blocks and versioned; notes are
            // plain content, with no lyrics or history to navigate to.
            links.add(linkTo(methodOn(SongBlockRestController.class).list(id, null, null))
                    .withRel(ApiRel.SONG_BLOCKS));
            links.add(linkTo(methodOn(SongVersionRestController.class).list(id, null, null))
                    .withRel(ApiRel.VERSIONS));
            links.add(linkTo(methodOn(SongEditionRestController.class).list(id, null))
                    .withRel(ApiRel.EDITIONS));
        }
        if (projectId != null) {
            links.add(linkTo(methodOn(TextDocumentRestController.class).list(projectId, null, null))
                    .withRel(ApiRel.DOCUMENTS));
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                    .withRel(ApiRel.PROJECT));
        }
        // Exporting is a read, so it sits outside the edit gate — a
        // collaborator with view-only access can still take a copy away. Not
        // song-only any more: SongExportService lays out a title and its lines,
        // which is what a note is too, and it already fell back to the
        // document's own text for songs with no blocks.
        links.add(linkTo(methodOn(TextDocumentController.class).exportSong(id, "txt", null))
                .withRel(ApiRel.EXPORT_SONG_TXT));
        links.add(linkTo(methodOn(TextDocumentController.class).exportSong(id, "pdf", null))
                .withRel(ApiRel.EXPORT_SONG_PDF));
        links.add(linkTo(methodOn(TextDocumentController.class).exportSong(id, "docx", null))
                .withRel(ApiRel.EXPORT_SONG_DOCX));
        links.add(linkTo(methodOn(TextDocumentController.class).exportSong(id, "epub", null))
                .withRel(ApiRel.EXPORT_SONG_EPUB));
        // The exception. A score is not a document, and the endpoint refuses a
        // note one — advertising it here would offer a download that 404s.
        if (TextDocument.TYPE_SONG.equalsIgnoreCase(type)) {
            links.add(linkTo(methodOn(TextDocumentController.class).exportSong(id, "musicxml", null))
                    .withRel(ApiRel.EXPORT_SONG_MUSICXML));
        }
        if (canEdit(projectId)) {
            links.add(linkTo(methodOn(TextDocumentRestController.class).update(id, null, null, null))
                    .withRel(ApiRel.UPDATE));
            links.add(linkTo(methodOn(TextDocumentRestController.class).delete(id, null, null))
                    .withRel(ApiRel.DELETE));
            links.add(linkTo(methodOn(TextDocumentRestController.class).insert(id, null, null))
                    .withRel(ApiRel.INSERT));
            links.add(linkTo(methodOn(TextDocumentRestController.class).duplicate(id, projectId, null))
                    .withRel(ApiRel.DUPLICATE));
            links.add(linkTo(methodOn(TextDocumentRestController.class).changeType(id, null, projectId, null))
                    .withRel(ApiRel.CHANGE_TYPE));
            // Songs and notes both archive: unlike the export and share rels
            // there is nothing song-shaped about putting a document aside.
            links.add(linkTo(methodOn(TextDocumentRestController.class).archive(id, projectId, null))
                    .withRel(ApiRel.ARCHIVE));
            // Emailing a note to a collaborator is the same act as emailing a
            // song: a title and the words under it, in one message. The share
            // service no longer skips notes, so nothing here has to either.
            links.add(linkTo(methodOn(TextDocumentRestController.class).shareEmail(id, null, null))
                    .withRel(ApiRel.SHARE_EMAIL));
        }
        return links.toArray(org.springframework.hateoas.Link[]::new);
    }

    private boolean canEdit(Integer projectId) {
        return projectAccess.canEditScriptForCurrentUser(projectId);
    }
}
