package com.scripty.api;

import com.scripty.controller.BlockRestController;
import com.scripty.controller.ProjectRestController;
import com.scripty.dto.Block;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.viewmodel.block.BlockViewModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@Component
public class BlockResourceAssembler implements RepresentationModelAssembler<BlockViewModel, EntityModel<BlockResource>> {

    @Autowired
    ProjectAccessSupport projectAccess;

    @Override
    public EntityModel<BlockResource> toModel(BlockViewModel block) {
        BlockResource resource = toResource(block);
        return EntityModel.of(resource).add(blockLinks(block.getId(), resource.getProjectId()));
    }

    public EntityModel<BlockResource> toModel(BlockViewModel block, Integer projectId) {
        BlockResource resource = toResource(block);
        resource.setProjectId(projectId);
        return EntityModel.of(resource).add(blockLinks(block.getId(), projectId));
    }

    public EntityModel<BlockResource> toModel(Block block) {
        BlockResource resource = toResource(block);
        Integer projectId = block.getProject() != null ? block.getProject().getId() : null;
        return EntityModel.of(resource).add(blockLinks(block.getId(), projectId));
    }

    public EntityModel<BlockResource> toDeleteModel(Block block) {
        BlockResource resource = new BlockResource();
        resource.setId(block.getId());
        Integer projectId = block.getProject() != null ? block.getProject().getId() : null;
        if (projectId != null) {
            return EntityModel.of(resource,
                    linkTo(methodOn(BlockRestController.class).list(projectId, null, null)).withRel(ApiRel.BLOCKS),
                    linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        return EntityModel.of(resource,
                linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS));
    }

    public CollectionModel<EntityModel<BlockResource>> toBlockCollection(Iterable<BlockViewModel> blocks, Integer projectId) {
        List<EntityModel<BlockResource>> resources = new ArrayList<>();
        for (BlockViewModel block : blocks) {
            resources.add(toModel(block, projectId));
        }
        CollectionModel<EntityModel<BlockResource>> collection = CollectionModel.of(resources)
                .add(linkTo(methodOn(BlockRestController.class).list(projectId, null, null)).withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        if (resources.isEmpty() && canEditProject(projectId)) {
            // Only an empty script can take a first block; see createInitial.
            collection.add(linkTo(methodOn(BlockRestController.class).createInitial(projectId, null))
                    .withRel(ApiRel.CREATE_INITIAL));
        }
        return collection;
    }

    private BlockResource toResource(BlockViewModel block) {
        BlockResource resource = new BlockResource();
        resource.setId(block.getId());
        resource.setOrder(block.getOrder());
        resource.setContent(block.getContent());
        resource.setType(block.getType());
        if (block.getPersonId() > 0) {
            resource.setPersonId(block.getPersonId());
            resource.setPersonName(block.getPersonName());
        }
        resource.setBookmarked(block.isBookmarked());
        resource.setPinned(block.isPinned());
        resource.setScene(block.isScene());
        resource.setTags(block.getTags());
        resource.setTextAlign(block.getTextAlign());
        resource.setFont(block.getFont());
        resource.setHighlight(block.getHighlight());
        resource.setTextBold(block.isTextBold());
        resource.setTextItalic(block.isTextItalic());
        resource.setTextUnderline(block.isTextUnderline());
        return resource;
    }

    private BlockResource toResource(Block block) {
        BlockResource resource = new BlockResource();
        resource.setId(block.getId());
        resource.setOrder(block.getOrder());
        resource.setContent(block.getContent());
        resource.setType(block.getType());
        resource.setBookmarked(block.isBookmarked());
        resource.setPinned(block.isPinned());
        resource.setScene(block.isScene());
        resource.setTags(block.getTags());
        resource.setTextAlign(block.getTextAlign());
        resource.setFont(block.getFont());
        resource.setHighlight(block.getHighlight());
        resource.setTextBold(block.isTextBold());
        resource.setTextItalic(block.isTextItalic());
        resource.setTextUnderline(block.isTextUnderline());
        if (block.getProject() != null) {
            resource.setProjectId(block.getProject().getId());
        }
        if (block.getPerson() != null) {
            resource.setPersonId(block.getPerson().getId());
            resource.setPersonName(block.getPerson().getName());
        }
        return resource;
    }

    private org.springframework.hateoas.Link[] blockLinks(int id, Integer projectId) {
        List<org.springframework.hateoas.Link> links = new ArrayList<>();
        boolean canEdit = canEdit(projectId, id);
        org.springframework.hateoas.Link self =
                linkTo(methodOn(BlockRestController.class).show(id, null)).withSelfRel();
        if (canEdit) {
            self = self
                    .andAffordance(afford(methodOn(BlockRestController.class).update(id, null, null, null)))
                    .andAffordance(afford(methodOn(BlockRestController.class).delete(id, null)));
        }
        links.add(self);
        if (canEdit) {
            links.add(linkTo(methodOn(BlockRestController.class).update(id, null, null, null)).withRel(ApiRel.UPDATE));
            links.add(linkTo(methodOn(BlockRestController.class).delete(id, null)).withRel(ApiRel.DELETE));
            links.add(linkTo(methodOn(BlockRestController.class).toggleBookmark(id, null)).withRel(ApiRel.TOGGLE_BOOKMARK));
            links.add(linkTo(methodOn(BlockRestController.class).togglePinned(id, null)).withRel(ApiRel.TOGGLE_PINNED));
            links.add(linkTo(methodOn(BlockRestController.class).createBelow(id, null, null)).withRel(ApiRel.CREATE_BELOW));
            links.add(linkTo(methodOn(BlockRestController.class).setType(id, null, null)).withRel(ApiRel.SET_TYPE));
            links.add(linkTo(methodOn(BlockRestController.class).move(id, null, null)).withRel(ApiRel.MOVE));
        }
        if (projectId != null) {
            links.add(linkTo(methodOn(BlockRestController.class).list(projectId, null, null)).withRel(ApiRel.BLOCKS));
            links.add(linkTo(methodOn(ProjectRestController.class).show(projectId, null)).withRel(ApiRel.PROJECT));
        }
        return links.toArray(org.springframework.hateoas.Link[]::new);
    }

    private boolean canEditProject(Integer projectId) {
        return projectAccess.canEditScriptForCurrentUser(projectId);
    }

    private boolean canEdit(Integer projectId, int blockId) {
        return projectId != null
                ? projectAccess.canEditScriptForCurrentUser(projectId)
                : projectAccess.canEditBlockForCurrentUser(blockId);
    }
}
