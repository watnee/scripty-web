package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.DeletedSongBlockResource;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import com.scripty.service.SongUndoRedoService;
import com.scripty.service.SongVersionService;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlockViewModel;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlocksViewModel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Recovery for deleted lyric lines — the REST counterpart of the song editor's
 * "recently deleted lines" page, and the song-side twin of
 * {@link BlockTrashRestController}.
 *
 * <p>Deleting a line over the API has always soft-deleted it, exactly as the
 * web editor does, but nothing advertised a way back. That left every delete
 * final from an API client and reversible from the browser, for the same lines.
 *
 * <p>Reading the trash needs only project access, matching the web page, which
 * shows a reader what was cut without letting them act on it. Restoring and
 * purging need script edit permission, so those two links appear only for a
 * caller who could use them.
 */
@RestController
@RequestMapping("/api/song/block/trash")
public class SongBlockTrashRestController {

    @Autowired
    SongBlockService songBlockService;

    @Autowired
    SongUndoRedoService songUndoRedoService;

    @Autowired
    SongVersionService songVersionService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@RequestParam Integer documentId, Principal principal) {
        if (!canAccess(documentId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    /**
     * Puts a line back where it was, into the song version it was deleted from.
     *
     * <p>The refreshed trash is returned rather than the line: the caller is
     * looking at this list, and the restored line belongs to the block
     * collection, which they can follow {@code songBlocks} to re-read.
     */
    @RequestMapping(value = "/{id}/restore", method = RequestMethod.POST, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> restore(@PathVariable Integer id,
                                     @RequestParam Integer documentId,
                                     Principal principal) {
        if (!canEdit(documentId, principal) || !canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Mirrors the web editor: a checkpoint first, so undo can take the
        // restore back, and the edition is read before the restore moves it.
        songUndoRedoService.recordCheckpointForBlock(id);
        Integer editionId = songBlockService.editionIdForBlock(id);
        Integer restoredDocumentId = songBlockService.restoreBlock(id);
        if (restoredDocumentId == null) {
            // Already restored, already purged, or never in this song's trash.
            // All three are "not here", and telling them apart would leak
            // whether the id exists in a song the caller cannot see.
            return ResponseEntity.notFound().build();
        }
        songVersionService.autoSaveVersion(restoredDocumentId, editionId);
        return ResponseEntity.ok(collection(restoredDocumentId, principal));
    }

    /** Deletes a line from the trash for good. */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> purge(@PathVariable Integer id,
                                   @RequestParam Integer documentId,
                                   Principal principal) {
        if (!canEdit(documentId, principal) || !canEditBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (songBlockService.purgeBlock(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(documentId, principal));
    }

    private boolean canAccess(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canAccessProject(projectId, principal);
    }

    private boolean canEdit(Integer documentId, Principal principal) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    /**
     * The id names a line in the trash, which may belong to another song than
     * the one the caller named. Checking both stops a caller from reaching into
     * a song they cannot edit by passing one they can.
     */
    private boolean canEditBlock(Integer blockId, Principal principal) {
        Integer projectId = songBlockService.projectIdForBlock(blockId);
        return projectId != null && projectAccess.canEditScript(projectId, principal);
    }

    private CollectionModel<EntityModel<DeletedSongBlockResource>> collection(
            Integer documentId, Principal principal) {
        DeletedSongBlocksViewModel viewModel = songBlockService.getDeletedBlocksViewModel(documentId);
        boolean canEdit = canEdit(documentId, principal);
        boolean unlimited = viewModel != null && viewModel.isRetentionUnlimited();

        List<EntityModel<DeletedSongBlockResource>> resources = new ArrayList<>();
        if (viewModel != null && viewModel.getBlocks() != null) {
            for (DeletedSongBlockViewModel block : viewModel.getBlocks()) {
                resources.add(EntityModel.of(toResource(block, unlimited),
                        itemLinks(block.getId(), documentId, canEdit)));
            }
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(SongBlockTrashRestController.class).list(documentId, null)).withSelfRel())
                .add(linkTo(methodOn(SongBlockRestController.class).list(documentId, null, null))
                        .withRel(ApiRel.SONG_BLOCKS))
                .add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.SONG));
    }

    private DeletedSongBlockResource toResource(DeletedSongBlockViewModel block, boolean retentionUnlimited) {
        DeletedSongBlockResource resource = new DeletedSongBlockResource();
        resource.setId(block.getId());
        resource.setContent(block.getContent());
        resource.setBlank(block.isBlank());
        resource.setHighlight(block.getHighlight());
        resource.setDeletedAt(ApiDates.toOffset(block.getDeletedAt()));
        // Left absent where nothing will ever purge the line, so a client does
        // not draw a countdown that will not run out.
        resource.setPurgeAt(retentionUnlimited ? null : ApiDates.toOffset(block.getPurgesAt()));
        return resource;
    }

    private Link[] itemLinks(Integer id, Integer documentId, boolean canEdit) {
        if (!canEdit) {
            return new Link[]{
                    linkTo(methodOn(SongBlockTrashRestController.class).list(documentId, null))
                            .withRel(ApiRel.TRASH)
            };
        }
        return new Link[]{
                linkTo(methodOn(SongBlockTrashRestController.class).restore(id, documentId, null))
                        .withRel(ApiRel.RESTORE),
                linkTo(methodOn(SongBlockTrashRestController.class).purge(id, documentId, null))
                        .withRel(ApiRel.PURGE),
                linkTo(methodOn(SongBlockTrashRestController.class).list(documentId, null))
                        .withRel(ApiRel.TRASH)
        };
    }
}
