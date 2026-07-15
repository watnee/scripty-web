package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.TextDocument;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SongBlockServiceImpl implements SongBlockService {

    private final TextDocumentRepository textDocumentRepository;
    private final BlockRepository blockRepository;
    private final ProjectRepository projectRepository;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public SongBlockServiceImpl(TextDocumentRepository textDocumentRepository,
                                BlockRepository blockRepository,
                                ProjectRepository projectRepository,
                                ProjectActivityService projectActivityService) {
        this.textDocumentRepository = textDocumentRepository;
        this.blockRepository = blockRepository;
        this.projectRepository = projectRepository;
        this.projectActivityService = projectActivityService;
    }

    @Override
    public Block read(Integer blockId) {
        if (blockId == null) {
            return null;
        }
        return blockRepository.findById(blockId).orElse(null);
    }

    @Override
    @Transactional
    public List<Block> ensureBlocks(Integer documentId) {
        TextDocument doc = documentId != null
                ? textDocumentRepository.findById(documentId).orElse(null)
                : null;
        if (doc == null || doc.getProject() == null) {
            return List.of();
        }
        List<Block> existing = blockRepository.findByTextDocumentIdOrderByOrderAsc(documentId);
        if (!existing.isEmpty()) {
            return existing;
        }

        List<String> lines = splitContentIntoLines(doc.getContent());
        List<Block> created = new ArrayList<>();
        int order = 1;
        for (String line : lines) {
            created.add(persistNewBlock(doc, line, order++));
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Block> listBlocks(Integer documentId) {
        if (documentId == null) {
            return List.of();
        }
        return blockRepository.findByTextDocumentIdOrderByOrderAsc(documentId);
    }

    @Override
    @Transactional
    public Block createBelow(Integer afterBlockId, String content) {
        Block after = songBlock(afterBlockId);
        if (after == null) {
            return null;
        }
        Integer docId = after.getTextDocument().getId();
        int afterOrder = after.getOrder();
        // Bulk order-shift clears the persistence context, so reload a managed doc afterward.
        blockRepository.incrementOrdersAboveDoc(afterOrder, docId);
        TextDocument doc = managedDoc(docId);
        if (doc == null) {
            return null;
        }
        Block created = persistNewBlock(doc, content, afterOrder + 1);
        afterMutation(doc);
        return created;
    }

    @Override
    @Transactional
    public Block createAtEnd(Integer documentId) {
        TextDocument doc = documentId != null
                ? textDocumentRepository.findById(documentId).orElse(null)
                : null;
        if (doc == null || doc.getProject() == null) {
            return null;
        }
        int order = blockRepository.countByTextDocumentId(documentId) + 1;
        Block created = persistNewBlock(doc, "", order);
        afterMutation(doc);
        return created;
    }

    @Override
    @Transactional
    public Block editContent(Integer blockId, String content) {
        Block block = songBlock(blockId);
        if (block == null) {
            return null;
        }
        block.setContent(PlainTextSanitizer.sanitize(content != null ? content : ""));
        blockRepository.save(block);
        afterMutation(block.getTextDocument());
        return block;
    }

    @Override
    @Transactional
    public Block delete(Integer blockId) {
        Block block = songBlock(blockId);
        if (block == null) {
            return null;
        }
        Integer docId = block.getTextDocument().getId();
        // Never leave the editor with zero blocks: clear the last one in place instead.
        if (blockRepository.countByTextDocumentId(docId) <= 1) {
            block.setContent("");
            blockRepository.save(block);
            afterMutation(managedDoc(docId));
            return block;
        }
        int order = block.getOrder();
        blockRepository.delete(block);
        // Bulk order-shift clears the persistence context, so reload a managed doc afterward.
        blockRepository.decrementOrdersAboveDoc(order, docId);
        afterMutation(managedDoc(docId));
        return block;
    }

    @Override
    @Transactional
    public Block moveUp(Integer blockId) {
        Block block = songBlock(blockId);
        if (block == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        Block above = blockRepository
                .findByTextDocumentIdAndOrder(doc.getId(), block.getOrder() - 1)
                .orElse(null);
        if (above != null) {
            int tmp = above.getOrder();
            above.setOrder(block.getOrder());
            block.setOrder(tmp);
            blockRepository.save(above);
            blockRepository.save(block);
            afterMutation(doc);
        }
        return block;
    }

    @Override
    @Transactional
    public Block moveDown(Integer blockId) {
        Block block = songBlock(blockId);
        if (block == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        Block below = blockRepository
                .findByTextDocumentIdAndOrder(doc.getId(), block.getOrder() + 1)
                .orElse(null);
        if (below != null) {
            int tmp = below.getOrder();
            below.setOrder(block.getOrder());
            block.setOrder(tmp);
            blockRepository.save(below);
            blockRepository.save(block);
            afterMutation(doc);
        }
        return block;
    }

    @Override
    @Transactional
    public Block moveTo(Integer blockId, int newOrder) {
        Block block = songBlock(blockId);
        if (block == null) {
            return null;
        }
        Integer docId = block.getTextDocument().getId();
        int currentOrder = block.getOrder();
        int count = blockRepository.countByTextDocumentId(docId);
        int clamped = Math.max(1, Math.min(newOrder, count));
        if (clamped == currentOrder) {
            return block;
        }
        // Bulk order-shift clears the persistence context; re-fetch the block, then a managed doc.
        if (clamped < currentOrder) {
            blockRepository.incrementOrdersInRangeDoc(clamped, currentOrder, docId);
        } else {
            blockRepository.decrementOrdersInRangeDoc(currentOrder, clamped, docId);
        }
        Block moved = blockRepository.findById(block.getId()).orElse(null);
        if (moved == null) {
            return null;
        }
        moved.setOrder(clamped);
        blockRepository.save(moved);
        afterMutation(managedDoc(docId));
        return moved;
    }

    /** A block that genuinely belongs to a song/text-document editor (guards against screenplay blocks). */
    private Block songBlock(Integer blockId) {
        Block block = read(blockId);
        if (block == null || block.getTextDocument() == null || block.getProject() == null) {
            return null;
        }
        return block;
    }

    /** Reload a document as a managed entity (needed after bulk order-shifts clear the context). */
    private TextDocument managedDoc(Integer docId) {
        return docId != null ? textDocumentRepository.findById(docId).orElse(null) : null;
    }

    private Block persistNewBlock(TextDocument doc, String content, int order) {
        Block block = new Block();
        block.setContent(PlainTextSanitizer.sanitize(content != null ? content : ""));
        block.setType(SONG_BLOCK_TYPE);
        block.setProject(doc.getProject());
        block.setTextDocument(doc);
        block.setBookmarked(false);
        block.setPinned(false);
        block.setSceneDelimiter(false);
        block.setOrder(order);
        return blockRepository.save(block);
    }

    /**
     * Recompute the document's {@code content} from its blocks (the newline-join is the single
     * source of truth for insert/share/export), bump timestamps, and record activity.
     */
    private void afterMutation(TextDocument doc) {
        if (doc == null) {
            return;
        }
        List<Block> blocks = blockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            String c = blocks.get(i).getContent();
            sb.append(c != null ? c : "");
        }
        LocalDateTime now = LocalDateTime.now();
        doc.setContent(sb.toString());
        doc.setUpdatedAt(now);
        textDocumentRepository.save(doc);

        Project project = doc.getProject();
        if (project != null) {
            project.setLastEdited(now);
            projectRepository.save(project);
            projectActivityService.recordForCurrentUser(
                    project.getId(),
                    ProjectActivity.ACTION_DOCUMENT_UPDATED,
                    "updated \"" + doc.getTitle() + "\"",
                    ProjectActivity.ENTITY_DOCUMENT,
                    doc.getId());
        }
    }

    /** Split raw content into lines, trimming only leading/trailing blank lines; blank when empty. */
    private List<String> splitContentIntoLines(String content) {
        String text = content != null ? content : "";
        String[] raw = text.split("\\R", -1);
        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            lines.add(line != null ? line : "");
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        while (!lines.isEmpty() && lines.get(0).trim().isEmpty()) {
            lines.remove(0);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }
}
