package com.scripty.api;

import com.scripty.controller.ActorRestController;
import com.scripty.controller.BlockRestController;
import com.scripty.config.FeatureFlag;
import com.scripty.config.FeatureFlags;
import com.scripty.controller.ContactSuggestionRestController;
import com.scripty.controller.InvitationRestController;
import com.scripty.controller.PersonRestController;
import com.scripty.controller.ProjectActivityRestController;
import com.scripty.controller.ProjectController;
import com.scripty.controller.ProjectRestController;
import com.scripty.controller.ProjectTrashRestController;
import com.scripty.controller.ProjectVersionRestController;
import com.scripty.controller.ScriptEditionRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.viewmodel.project.projectlist.ProjectTeamViewModel;
import com.scripty.viewmodel.project.projectlist.ProjectViewModel;
import com.scripty.viewmodel.project.projectprofile.ProjectProfileViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.scripty.security.ProjectAccessSupport;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class ProjectResourceAssembler implements RepresentationModelAssembler<ProjectViewModel, EntityModel<ProjectResource>> {

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    FeatureFlags featureFlags;

    @Override
    public EntityModel<ProjectResource> toModel(ProjectViewModel project) {
        return EntityModel.of(toResource(project)).add(projectLinks(project.getId()));
    }

    public EntityModel<ProjectResource> toModel(ProjectProfileViewModel profile) {
        ProjectResource resource = new ProjectResource();
        resource.setId(profile.getId());
        resource.setTitle(profile.getTitle());
        resource.setScreenplayTitle(profile.getScreenplayTitle());
        resource.setWriters(profile.getWriters());
        resource.setContactInfo(profile.getContactInfo());
        resource.setScreenplayVersion(profile.getScreenplayVersion());
        resource.setLastEdited(ApiDates.toOffset(profile.getLastEdited()));
        resource.setTeams(profile.getTeams());
        return EntityModel.of(resource).add(projectLinks(profile.getId()));
    }

    public EntityModel<ProjectResource> toModel(Project project) {
        ProjectResource resource = new ProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        resource.setScreenplayTitle(project.getScreenplayTitle());
        resource.setWriters(project.getWriters());
        resource.setContactInfo(project.getContactInfo());
        resource.setScreenplayVersion(project.getScreenplayVersion());
        resource.setLastEdited(ApiDates.toOffset(project.getLastEdited()));
        resource.setTeams(project.getTeamNames());
        return EntityModel.of(resource).add(projectLinks(project.getId()));
    }

    public EntityModel<ProjectResource> toDeleteModel(Project project) {
        ProjectResource resource = new ProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        return EntityModel.of(resource,
                linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS));
    }

    public CollectionModel<EntityModel<ProjectResource>> toProjectCollection(Iterable<ProjectViewModel> projects) {
        return toProjectCollection(projects, null);
    }

    public CollectionModel<EntityModel<ProjectResource>> toProjectCollection(
            Iterable<ProjectViewModel> projects, Integer defaultProjectId) {
        List<EntityModel<ProjectResource>> resources = new ArrayList<>();
        for (ProjectViewModel project : projects) {
            EntityModel<ProjectResource> model = toModel(project);
            if (defaultProjectId != null && defaultProjectId.equals(project.getId())
                    && model.getContent() != null) {
                model.getContent().setDefault(true);
            }
            resources.add(model);
        }
        return CollectionModel.of(resources)
                .add(linkTo(methodOn(ProjectRestController.class).list(null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).importProject(null)).withRel(ApiRel.IMPORT_PROJECT))
                // Deleting a project is a soft delete, so the collection also
                // points at where the deleted ones went.
                .add(linkTo(methodOn(ProjectTrashRestController.class).list(null)).withRel(ApiRel.TRASH));
    }

    private ProjectResource toResource(ProjectViewModel project) {
        ProjectResource resource = new ProjectResource();
        resource.setId(project.getId());
        resource.setTitle(project.getTitle());
        resource.setLastEdited(ApiDates.toOffset(project.getLastEdited()));
        if (project.getTeams() != null) {
            resource.setTeams(project.getTeams().stream()
                    .map(ProjectTeamViewModel::getName)
                    .collect(Collectors.toList()));
        }
        return resource;
    }

    /**
     * Script import is advertised only to users who may edit the screenplay,
     * matching the permission the endpoint itself enforces.
     */
    private boolean canEditScript(Integer projectId) {
        return projectAccess.canEditScriptForCurrentUser(projectId);
    }

    private org.springframework.hateoas.Link[] projectLinks(int id) {
        List<org.springframework.hateoas.Link> links = new ArrayList<>(List.of(baseProjectLinks(id)));
        if (canEditScript(id)) {
            links.add(linkTo(methodOn(ProjectRestController.class).importScript(id, null, null, null))
                    .withRel(ApiRel.IMPORT_SCRIPT));
            // Only when the flag is on, so a client sees no invitation surface
            // at all until the endpoints actually answer.
            if (featureFlags.isEnabled(FeatureFlag.API_INVITATIONS)) {
                links.add(linkTo(methodOn(InvitationRestController.class).list(id, null))
                        .withRel(ApiRel.INVITATIONS));
            }
        }
        return links.toArray(new org.springframework.hateoas.Link[0]);
    }

    private org.springframework.hateoas.Link[] baseProjectLinks(int id) {
        org.springframework.hateoas.Link self =
                linkTo(methodOn(ProjectRestController.class).show(id, null)).withSelfRel()
                        .andAffordance(afford(methodOn(ProjectRestController.class).update(id, null, null, null)))
                        .andAffordance(afford(methodOn(ProjectRestController.class).delete(id, null)));
        return new org.springframework.hateoas.Link[]{
                self,
                linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS),
                linkTo(methodOn(ProjectRestController.class).update(id, null, null, null)).withRel(ApiRel.UPDATE),
                linkTo(methodOn(ProjectRestController.class).delete(id, null)).withRel(ApiRel.DELETE),
                linkTo(methodOn(ProjectRestController.class).toggleDefault(id, null)).withRel(ApiRel.TOGGLE_DEFAULT),
                linkTo(methodOn(BlockRestController.class).list(id, null, null)).withRel(ApiRel.BLOCKS),
                linkTo(methodOn(PersonRestController.class).list(id, null)).withRel(ApiRel.CHARACTERS),
                linkTo(methodOn(ActorRestController.class).list(id, null)).withRel(ApiRel.ACTORS),
                linkTo(methodOn(TextDocumentRestController.class).list(id, null, null)).withRel(ApiRel.DOCUMENTS),
                linkTo(methodOn(TextDocumentRestController.class).list(id, TextDocument.TYPE_SONG, null)).withRel(ApiRel.SONGS),
                linkTo(methodOn(TextDocumentRestController.class).list(id, TextDocument.TYPE_NOTES, null)).withRel(ApiRel.NOTES),
                linkTo(methodOn(ProjectVersionRestController.class).list(id, null, null)).withRel(ApiRel.VERSIONS),
                linkTo(methodOn(ScriptEditionRestController.class).list(id, null)).withRel(ApiRel.EDITIONS),
                linkTo(methodOn(ProjectActivityRestController.class).list(id, null, null)).withRel(ApiRel.ACTIVITY),
                linkTo(methodOn(ContactSuggestionRestController.class).suggest(id, null, null))
                        .withRel(ApiRel.CONTACT_SUGGESTIONS),
                linkTo(methodOn(ProjectController.class).syncStatus(id, null, null, null)).withRel(ApiRel.SYNC_STATUS),
                linkTo(methodOn(ProjectController.class).undoRedoStatus(id, null, null)).withRel(ApiRel.UNDO_REDO_STATUS),
                linkTo(methodOn(ProjectController.class).exportScript(id, "fountain", null, null, null, null)).withRel(ApiRel.EXPORT),
                linkTo(methodOn(ProjectController.class).exportScript(id, "pdf", null, null, null, null)).withRel(ApiRel.EXPORT_PDF),
                linkTo(methodOn(ProjectController.class).exportScript(id, "docx", null, null, null, null)).withRel(ApiRel.EXPORT_DOCX),
                linkTo(methodOn(ProjectController.class).exportScript(id, "fdx", null, null, null, null)).withRel(ApiRel.EXPORT_FDX),
                // The same endpoint has always served these two; only the
                // advertisement was missing, so API clients could not find
                // them the way the web menu could.
                linkTo(methodOn(ProjectController.class).exportScript(id, "epub", null, null, null, null)).withRel(ApiRel.EXPORT_EPUB),
                linkTo(methodOn(ProjectController.class).exportScript(id, "scripty", null, null, null, null)).withRel(ApiRel.EXPORT_ARCHIVE)
        };
    }
}
