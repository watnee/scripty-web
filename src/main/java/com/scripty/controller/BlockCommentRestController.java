package com.scripty.controller;

import com.scripty.api.AddCommentRequest;
import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.BlockCommentResource;
import com.scripty.dto.BlockComment;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockCommentService;
import java.security.Principal;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Comments on screenplay elements.
 *
 * <p>The MVC handlers return JSON already, but they are form-encoded, live
 * under {@code /block/**} and answer with a {@code canDelete} flag. This is the
 * hypermedia equivalent: same service, same authorization, but permission
 * expressed as the presence of a {@code delete} link.
 *
 * <p>The rule that decides that link is worth stating because it is easy to
 * lose in a port: a comment may be removed by anyone who can edit the block —
 * that is, by the people responsible for the script — <em>or</em> by whoever
 * wrote it. A reader who left a note can always take it back.
 */
@RestController
@RequestMapping("/api/block")
public class BlockCommentRestController {

    @Autowired
    BlockCommentService blockCommentService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(value = "/{id}/comments", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(id, principal));
    }

    @RequestMapping(value = "/{id}/comments", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> add(@PathVariable Integer id,
                                 @RequestBody AddCommentRequest request,
                                 Principal principal) {
        // Commenting needs only read access to the script: leaving a note is
        // how someone who may not edit contributes.
        if (!projectAccess.canAccessBlock(id, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.body() == null || request.body().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("body", "You must supply a comment."), HttpStatus.BAD_REQUEST);
        }
        User author = projectAccess.currentUser(principal);
        if (blockCommentService.addComment(id, author, request.body()) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(collection(id, principal));
    }

    @RequestMapping(value = "/comments/{commentId}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> delete(@PathVariable Integer commentId, Principal principal) {
        BlockComment comment = blockCommentService.read(commentId);
        if (comment == null || comment.getBlock() == null) {
            return ResponseEntity.notFound().build();
        }
        Integer blockId = comment.getBlock().getId();
        if (!projectAccess.canAccessBlock(blockId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!canDelete(comment, projectAccess.currentUser(principal),
                       projectAccess.canEditBlock(blockId, principal))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        blockCommentService.delete(commentId);
        return ResponseEntity.ok(collection(blockId, principal));
    }

    /**
     * A comment may be removed by anyone who can edit the block, or by its
     * author. Ported verbatim from the MVC controller — losing the second half
     * would strand a reader's own note.
     */
    private boolean canDelete(BlockComment comment, User current, boolean canEditBlock) {
        if (canEditBlock) {
            return true;
        }
        if (current == null || current.getId() == null || comment.getAuthor() == null) {
            return false;
        }
        return current.getId().equals(comment.getAuthor().getId());
    }

    private CollectionModel<EntityModel<BlockCommentResource>> collection(
            Integer blockId, Principal principal) {
        boolean canEdit = projectAccess.canEditBlock(blockId, principal);
        User current = projectAccess.currentUser(principal);

        List<EntityModel<BlockCommentResource>> resources = new ArrayList<>();
        for (BlockComment comment : blockCommentService.listForBlock(blockId)) {
            List<Link> links = new ArrayList<>();
            links.add(linkTo(methodOn(BlockCommentRestController.class).list(blockId, null))
                    .withRel(ApiRel.COMMENTS));
            if (canDelete(comment, current, canEdit)) {
                links.add(linkTo(methodOn(BlockCommentRestController.class)
                        .delete(comment.getId(), null)).withRel(ApiRel.DELETE));
            }
            resources.add(EntityModel.of(toResource(comment, blockId), links));
        }

        return CollectionModel.of(resources)
                .add(linkTo(methodOn(BlockCommentRestController.class).list(blockId, null)).withSelfRel())
                .add(linkTo(methodOn(BlockCommentRestController.class).add(blockId, null, null))
                        .withRel(ApiRel.ADD_COMMENT))
                .add(linkTo(methodOn(BlockRestController.class).show(blockId, null)).withRel("block"));
    }

    private BlockCommentResource toResource(BlockComment comment, Integer blockId) {
        BlockCommentResource resource = new BlockCommentResource();
        resource.setId(comment.getId());
        resource.setBlockId(blockId);
        if (comment.getAuthor() != null) {
            resource.setAuthorId(comment.getAuthor().getId());
        }
        resource.setAuthorName(comment.getAuthorName());
        resource.setBody(comment.getBody());
        resource.setCreatedAt(ApiDates.toOffset(comment.getCreatedAt()));
        return resource;
    }
}
