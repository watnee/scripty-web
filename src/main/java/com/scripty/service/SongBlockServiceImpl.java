package com.scripty.service;

import com.scripty.dto.Project;
import com.scripty.dto.SongBlock;
import com.scripty.dto.TextDocument;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SongBlockServiceImpl implements SongBlockService {

    private final SongBlockRepository songBlockRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final ProjectRepository projectRepository;

    @Autowired
    public SongBlockServiceImpl(SongBlockRepository songBlockRepository,
                                TextDocumentRepository textDocumentRepository,
                                ProjectRepository projectRepository) {
        this.songBlockRepository = songBlockRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public SongBlock read(Integer id) {
        if (id == null) {
            return null;
        }
        return songBlockRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer projectIdForBlock(Integer blockId) {
        SongBlock block = read(blockId);
        if (block == null) {
            return null;
        }
        return projectIdForDocument(block.getTextDocument() != null ? block.getTextDocument().getId() : null);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer projectIdForDocument(Integer documentId) {
        if (documentId == null) {
            return null;
        }
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getProject() == null) {
            return null;
        }
        return doc.getProject().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Integer documentIdForBlock(Integer blockId) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        return block.getTextDocument().getId();
    }

    @Override
    @Transactional
    public List<SongBlockViewModel> getBlocks(Integer documentId) {
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return List.of();
        }
        List<SongBlock> blocks = ensureSeeded(doc);
        return toViewModels(blocks);
    }

    @Override
    @Transactional
    public SongBlock appendBlock(Integer documentId) {
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            return null;
        }
        List<SongBlock> blocks = new ArrayList<>(ensureSeeded(doc));
        SongBlock created = newBlock(doc, "");
        blocks.add(created);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return created;
    }

    @Override
    @Transactional
    public SongBlock createBelow(Integer afterBlockId, String afterContent) {
        SongBlock after = read(afterBlockId);
        if (after == null || after.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = after.getTextDocument();
        if (afterContent != null) {
            after.setContent(PlainTextSanitizer.sanitize(afterContent));
            after.setUpdatedAt(LocalDateTime.now());
        }
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId()));
        int idx = indexOf(blocks, afterBlockId);
        SongBlock created = newBlock(doc, "");
        blocks.add(idx + 1, created);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return created;
    }

    @Override
    @Transactional
    public SongBlock editContent(Integer blockId, String content) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        block.setContent(PlainTextSanitizer.sanitize(content != null ? content : ""));
        block.setUpdatedAt(LocalDateTime.now());
        songBlockRepository.save(block);
        rebuildDocumentContent(block.getTextDocument(), null);
        return block;
    }

    @Override
    @Transactional
    public Integer deleteBlock(Integer blockId) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId()));
        blocks.removeIf(b -> b.getId().equals(blockId));
        songBlockRepository.delete(block);
        if (blocks.isEmpty()) {
            blocks.add(newBlock(doc, ""));
        }
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return doc.getId();
    }

    @Override
    @Transactional
    public SongBlock moveUp(Integer blockId) {
        return move(blockId, -1);
    }

    @Override
    @Transactional
    public SongBlock moveDown(Integer blockId) {
        return move(blockId, 1);
    }

    private SongBlock move(Integer blockId, int delta) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId()));
        int idx = indexOf(blocks, blockId);
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= blocks.size()) {
            return block;
        }
        SongBlock moved = blocks.remove(idx);
        blocks.add(target, moved);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return moved;
    }

    // --- helpers ---------------------------------------------------------

    private List<SongBlock> ensureSeeded(TextDocument doc) {
        List<SongBlock> existing = songBlockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        List<SongBlock> seeded = new ArrayList<>();
        for (String line : splitContentIntoLines(doc.getContent())) {
            seeded.add(newBlock(doc, line));
        }
        if (seeded.isEmpty()) {
            seeded.add(newBlock(doc, ""));
        }
        renumberAndSave(seeded);
        // Keep content byte-identical to the block join so nothing drifts.
        rebuildDocumentContent(doc, seeded);
        return seeded;
    }

    private SongBlock newBlock(TextDocument doc, String content) {
        LocalDateTime now = LocalDateTime.now();
        SongBlock block = new SongBlock();
        block.setTextDocument(doc);
        block.setContent(PlainTextSanitizer.sanitize(content != null ? content : ""));
        block.setCreatedAt(now);
        block.setUpdatedAt(now);
        return block;
    }

    private void renumberAndSave(List<SongBlock> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).setOrder(i);
        }
        songBlockRepository.saveAll(blocks);
    }

    private void rebuildDocumentContent(TextDocument doc, List<SongBlock> blocks) {
        List<SongBlock> ordered = blocks != null
                ? blocks
                : songBlockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId());
        String content = ordered.stream()
                .map(b -> b.getContent() != null ? b.getContent() : "")
                .collect(Collectors.joining("\n"));
        LocalDateTime now = LocalDateTime.now();
        doc.setContent(content);
        doc.setUpdatedAt(now);
        textDocumentRepository.save(doc);
        Project project = doc.getProject();
        if (project != null) {
            project.setLastEdited(now);
            projectRepository.save(project);
        }
    }

    private int indexOf(List<SongBlock> blocks, Integer blockId) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).getId() != null && blocks.get(i).getId().equals(blockId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Splits free-text content into lyric lines, trimming leading/trailing blank
     * lines but preserving blank lines in the middle (mirrors how songs are split
     * when inserted into the screenplay).
     */
    private List<String> splitContentIntoLines(String content) {
        List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        for (String line : content.split("\\R", -1)) {
            lines.add(line != null ? line : "");
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        while (!lines.isEmpty() && lines.get(0).trim().isEmpty()) {
            lines.remove(0);
        }
        return lines;
    }

    private List<SongBlockViewModel> toViewModels(List<SongBlock> blocks) {
        List<SongBlockViewModel> vms = new ArrayList<>();
        for (SongBlock b : blocks) {
            Integer docId = b.getTextDocument() != null ? b.getTextDocument().getId() : null;
            vms.add(new SongBlockViewModel(b.getId(), docId, b.getOrder(), b.getContent()));
        }
        return vms;
    }
}
