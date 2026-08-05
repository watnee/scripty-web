package com.scripty.api;

import com.scripty.controller.SongAudioRestController;
import com.scripty.controller.SongBlockRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.dto.SongAudio;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.SongBlockService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Builds HAL resources for a song's recordings.
 *
 * <p>Playing one is a read, so {@code audioFile} is advertised to anyone who
 * can open the song — a collaborator with view-only access hears the demo like
 * everyone else. Uploading, renaming and deleting appear only for an editor,
 * the rule every other assembler here follows, and the controller enforces it
 * regardless; these links only tell an honest client what to draw.
 */
@Component
public class SongAudioResourceAssembler {

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    SongBlockService songBlockService;

    public EntityModel<SongAudioResource> toModel(SongAudio audio, Integer documentId) {
        return EntityModel.of(toResource(audio, documentId), audioLinks(audio.getId(), documentId));
    }

    public CollectionModel<EntityModel<SongAudioResource>> toCollection(List<SongAudio> recordings,
                                                                       Integer documentId) {
        List<EntityModel<SongAudioResource>> resources = new ArrayList<>();
        for (SongAudio audio : recordings) {
            resources.add(toModel(audio, documentId));
        }
        Link self = linkTo(methodOn(SongAudioRestController.class).list(documentId, null)).withSelfRel();
        if (canEdit(documentId)) {
            self = self.andAffordance(afford(methodOn(SongAudioRestController.class)
                    .upload(documentId, null, null, null, null)));
        }
        CollectionModel<EntityModel<SongAudioResource>> collection = CollectionModel.of(resources)
                .add(self)
                .add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                        .withRel(ApiRel.SONG))
                .add(linkTo(methodOn(SongBlockRestController.class).list(documentId, null, null))
                        .withRel(ApiRel.SONG_BLOCKS));
        if (canEdit(documentId)) {
            collection.add(linkTo(methodOn(SongAudioRestController.class)
                    .upload(documentId, null, null, null, null)).withRel(ApiRel.UPLOAD_AUDIO));
        }
        return collection;
    }

    private SongAudioResource toResource(SongAudio audio, Integer documentId) {
        SongAudioResource resource = new SongAudioResource();
        resource.setId(audio.getId());
        resource.setDocumentId(documentId);
        resource.setTitle(audio.getTitle());
        resource.setFileName(audio.getFileName());
        resource.setContentType(audio.getContentType());
        resource.setByteSize(audio.getByteSize());
        resource.setDurationMs(audio.getDurationMs());
        resource.setSortOrder(audio.getSortOrder());
        resource.setCreatedAt(ApiDates.toOffset(audio.getCreatedAt()));
        return resource;
    }

    private Link[] audioLinks(Integer id, Integer documentId) {
        List<Link> links = new ArrayList<>();
        Link self = linkTo(methodOn(SongAudioRestController.class).show(id, documentId, null)).withSelfRel();
        if (canEdit(documentId)) {
            self = self
                    .andAffordance(afford(methodOn(SongAudioRestController.class)
                            .rename(id, documentId, null, null)))
                    .andAffordance(afford(methodOn(SongAudioRestController.class)
                            .delete(id, documentId, null)));
        }
        links.add(self);
        // The bytes. Every client needs this one — playing, downloading and
        // handing the take to a share sheet are all the same href.
        links.add(linkTo(methodOn(SongAudioRestController.class).file(id, documentId, null))
                .withRel(ApiRel.AUDIO_FILE));
        links.add(linkTo(methodOn(SongAudioRestController.class).list(documentId, null))
                .withRel(ApiRel.AUDIO_RECORDINGS));
        links.add(linkTo(methodOn(TextDocumentRestController.class).show(documentId, null))
                .withRel(ApiRel.SONG));
        if (canEdit(documentId)) {
            links.add(linkTo(methodOn(SongAudioRestController.class).rename(id, documentId, null, null))
                    .withRel(ApiRel.RENAME_AUDIO));
            links.add(linkTo(methodOn(SongAudioRestController.class).delete(id, documentId, null))
                    .withRel(ApiRel.DELETE_AUDIO));
        }
        return links.toArray(Link[]::new);
    }

    /** Whether the current user may write to the song these recordings belong to. */
    private boolean canEdit(Integer documentId) {
        Integer projectId = songBlockService.projectIdForDocument(documentId);
        return projectId != null && projectAccess.canEditScriptForCurrentUser(projectId);
    }
}
