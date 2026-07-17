package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.BlockComment;
import com.scripty.dto.User;
import com.scripty.repository.BlockCommentRepository;
import com.scripty.repository.BlockRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockCommentServiceImpl implements BlockCommentService {

    /** Comment bodies over this length are rejected before insert. */
    private static final int MAX_BODY_LENGTH = 4000;

    private final BlockCommentRepository blockCommentRepository;
    private final BlockRepository blockRepository;

    @Autowired
    public BlockCommentServiceImpl(BlockCommentRepository blockCommentRepository,
                                   BlockRepository blockRepository) {
        this.blockCommentRepository = blockCommentRepository;
        this.blockRepository = blockRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockComment> listForBlock(Integer blockId) {
        return blockCommentRepository.findByBlockIdOrderByCreatedAtAsc(blockId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countForBlock(Integer blockId) {
        return blockCommentRepository.countByBlockId(blockId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, Long> countsForProject(Integer projectId) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Object[] row : blockCommentRepository.countsByProject(projectId)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public BlockComment read(Integer commentId) {
        return blockCommentRepository.findById(commentId).orElse(null);
    }

    @Override
    @Transactional
    public BlockComment addComment(Integer blockId, User author, String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_BODY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_BODY_LENGTH);
        }
        Block block = blockRepository.findById(blockId).orElse(null);
        if (block == null) {
            return null;
        }

        BlockComment comment = new BlockComment();
        comment.setBlock(block);
        comment.setAuthor(author);
        comment.setAuthorName(displayNameFor(author));
        comment.setBody(trimmed);
        comment.setCreatedAt(LocalDateTime.now());
        return blockCommentRepository.save(comment);
    }

    @Override
    @Transactional
    public void delete(Integer commentId) {
        blockCommentRepository.deleteById(commentId);
    }

    /** First+last name when present, otherwise the username, otherwise "Unknown". */
    private String displayNameFor(User author) {
        if (author == null) {
            return "Unknown";
        }
        String first = author.getFirstName() != null ? author.getFirstName().trim() : "";
        String last = author.getLastName() != null ? author.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return author.getUsername() != null ? author.getUsername() : "Unknown";
    }
}
