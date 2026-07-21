package com.scripty.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

/**
 * HAL representation of a lyric line sitting in a song's trash.
 *
 * <p>Unlike {@link DeletedBlockResource} this carries the whole line rather than
 * a preview: a lyric line is short, and the writer deciding whether to bring it
 * back is reading the words, not scanning a list of scenes.
 *
 * <p>{@code purgeAt} is absent when the deployment keeps trashed lines until
 * someone deletes them for good. A client that treats a missing date as "gone
 * soon" would rush the writer into a decision nothing is forcing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Relation(itemRelation = ApiRel.DELETED_SONG_BLOCK, collectionRelation = ApiRel.DELETED_SONG_BLOCKS)
public class DeletedSongBlockResource extends RepresentationModel<DeletedSongBlockResource> {

    private Integer id;
    private String content;
    private boolean blank;
    private String highlight;
    private OffsetDateTime deletedAt;
    private OffsetDateTime purgeAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /** True when the line had no visible text, so a client can label it rather than draw nothing. */
    public boolean isBlank() {
        return blank;
    }

    public void setBlank(boolean blank) {
        this.blank = blank;
    }

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public OffsetDateTime getPurgeAt() {
        return purgeAt;
    }

    public void setPurgeAt(OffsetDateTime purgeAt) {
        this.purgeAt = purgeAt;
    }
}
