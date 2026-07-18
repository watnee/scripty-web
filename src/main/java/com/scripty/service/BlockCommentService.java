package com.scripty.service;

import com.scripty.dto.BlockComment;
import com.scripty.dto.User;
import java.util.List;
import java.util.Map;

public interface BlockCommentService {

    /** Comments on {@code blockId}, oldest first. */
    List<BlockComment> listForBlock(Integer blockId);

    /** Number of comments on {@code blockId}. */
    long countForBlock(Integer blockId);

    /** Comment counts keyed by block id for a project (blocks with none omitted). */
    Map<Integer, Long> countsForProject(Integer projectId);

    BlockComment read(Integer commentId);

    /**
     * Appends a comment to {@code blockId}. Returns null when the block does not
     * exist or {@code body} is blank.
     */
    BlockComment addComment(Integer blockId, User author, String body);

    void delete(Integer commentId);
}
