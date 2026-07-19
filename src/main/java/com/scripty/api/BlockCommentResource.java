package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of one comment on a screenplay element.
 *
 * <p>Whether the caller may remove a comment is expressed as the presence of a
 * {@code delete} link rather than as a {@code canDelete} flag: the MVC view
 * ships the flag because a Thymeleaf template needs it to decide what to draw,
 * but a hypermedia client should be told what it may do, not asked to work it
 * out from a boolean.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.COMMENT, collectionRelation = ApiRel.COMMENTS)
public class BlockCommentResource extends RepresentationModel<BlockCommentResource> {

    private Integer id;
    private Integer blockId;
    private Integer authorId;
    private String authorName;
    private String body;
    private OffsetDateTime createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getBlockId() {
        return blockId;
    }

    public void setBlockId(Integer blockId) {
        this.blockId = blockId;
    }

    public Integer getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Integer authorId) {
        this.authorId = authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
