package com.scripty.viewmodel.block;

import com.scripty.dto.BlockComment;
import java.time.format.DateTimeFormatter;

/**
 * JSON shape for a single block comment returned by the inline comment
 * endpoints. {@code canDelete} is resolved per request against the viewer.
 */
public class BlockCommentViewModel {

    private Integer id;
    private Integer blockId;
    private Integer authorId;
    private String authorName;
    private String body;
    private String createdAt;
    private boolean canDelete;

    public static BlockCommentViewModel from(BlockComment comment, boolean canDelete) {
        BlockCommentViewModel vm = new BlockCommentViewModel();
        vm.id = comment.getId();
        vm.blockId = comment.getBlock() != null ? comment.getBlock().getId() : null;
        vm.authorId = comment.getAuthor() != null ? comment.getAuthor().getId() : null;
        vm.authorName = comment.getAuthorName();
        vm.body = comment.getBody();
        vm.createdAt = comment.getCreatedAt() != null
                ? comment.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;
        vm.canDelete = canDelete;
        return vm;
    }

    public Integer getId() {
        return id;
    }

    public Integer getBlockId() {
        return blockId;
    }

    public Integer getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getBody() {
        return body;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isCanDelete() {
        return canDelete;
    }
}
