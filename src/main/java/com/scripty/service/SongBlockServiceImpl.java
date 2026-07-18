package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.SongBlock;
import com.scripty.dto.TextDocument;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlockViewModel;
import com.scripty.viewmodel.song.deletedblocks.DeletedSongBlocksViewModel;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SongBlockServiceImpl implements SongBlockService {

    private final SongBlockRepository songBlockRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final ProjectRepository projectRepository;

    // Trashed lines stay recoverable for this long; matches the document trash window.
    @Value("${scripty.songblocks.trash-retention-days:30}")
    private int trashRetentionDays = 30;

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
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
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
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return List.of();
        }
        List<SongBlock> blocks = ensureSeeded(doc);
        return toViewModels(blocks);
    }

    @Override
    @Transactional
    public SongBlock appendBlock(Integer documentId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
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
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId()));
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
    public SongBlock setHighlight(Integer blockId, String highlight) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        // The tint is not part of the lyrics, so the parent document's text is left alone.
        block.setHighlight(Block.normalizeHighlight(highlight));
        block.setUpdatedAt(LocalDateTime.now());
        songBlockRepository.save(block);
        return block;
    }

    @Override
    @Transactional
    public Integer deleteBlock(Integer blockId) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null || block.isDeleted()) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId()));
        blocks.removeIf(b -> b.getId().equals(blockId));
        // Soft delete: the line drops out of the song but is kept, so it can be
        // restored from the "recently deleted lines" recovery view.
        LocalDateTime now = LocalDateTime.now();
        block.setDeletedAt(now);
        block.setUpdatedAt(now);
        songBlockRepository.save(block);
        if (blocks.isEmpty()) {
            blocks.add(newBlock(doc, ""));
        }
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return doc.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public DeletedSongBlocksViewModel getDeletedBlocksViewModel(Integer documentId) {
        TextDocument doc = documentId != null
                ? textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null)
                : null;
        if (doc == null) {
            return null;
        }
        DeletedSongBlocksViewModel vm = new DeletedSongBlocksViewModel();
        vm.setDocumentId(doc.getId());
        vm.setSongTitle(doc.getTitle());
        if (doc.getProject() != null) {
            vm.setProjectId(doc.getProject().getId());
            vm.setProjectTitle(doc.getProject().getTitle());
        }
        List<DeletedSongBlockViewModel> deleted = new ArrayList<>();
        for (SongBlock block : songBlockRepository
                .findByTextDocumentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(doc.getId())) {
            deleted.add(new DeletedSongBlockViewModel(
                    block.getId(),
                    block.getContent(),
                    block.getHighlight(),
                    block.getDeletedAt(),
                    block.getDeletedAt() != null ? block.getDeletedAt().plusDays(trashRetentionDays) : null));
        }
        vm.setBlocks(deleted);
        return vm;
    }

    @Override
    @Transactional
    public Integer restoreBlock(Integer blockId) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null || !block.isDeleted()) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        LocalDateTime now = LocalDateTime.now();
        block.setDeletedAt(null);
        block.setUpdatedAt(now);
        // Send it back to the end of the song: its old order long since belongs to
        // another line, and the user can drag it wherever they want from there.
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId()));
        blocks.removeIf(b -> b.getId().equals(blockId));
        blocks.add(block);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return doc.getId();
    }

    @Override
    @Transactional
    public Integer purgeBlock(Integer blockId) {
        SongBlock block = read(blockId);
        // Only a trashed line can be purged, so this is never reachable from the
        // editor — deleting always leaves a recovery window first.
        if (block == null || !block.isDeleted()) {
            return null;
        }
        Integer documentId = block.getTextDocument() != null ? block.getTextDocument().getId() : null;
        songBlockRepository.delete(block);
        return documentId;
    }

    @Override
    @Transactional
    public int purgeExpiredBlocks() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(trashRetentionDays);
        List<SongBlock> expired = songBlockRepository.findByDeletedAtNotNullAndDeletedAtBefore(cutoff);
        songBlockRepository.deleteAll(expired);
        return expired.size();
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

    @Override
    @Transactional
    public SongBlock moveTo(Integer blockId, int position) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId()));
        int idx = indexOf(blocks, blockId);
        if (idx < 0) {
            return block;
        }
        int target = Math.max(0, Math.min(position, blocks.size() - 1));
        if (target == idx) {
            return block;
        }
        return reorder(doc, blocks, idx, target);
    }

    @Override
    @Transactional
    public List<LineSnapshot> snapshotLines(Integer documentId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return null;
        }
        return ensureSeeded(doc).stream()
                .map(b -> new LineSnapshot(b.getContent() != null ? b.getContent() : "", b.getHighlight()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void replaceLines(Integer documentId, List<LineSnapshot> lines) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null || lines == null) {
            return;
        }
        songBlockRepository.deleteAll(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId()));
        // Hibernate orders inserts before deletes at flush time; force the deletes
        // out first so a later read of this document does not see the old rows.
        songBlockRepository.flush();
        List<SongBlock> blocks = new ArrayList<>();
        for (LineSnapshot line : lines) {
            SongBlock block = newBlock(doc, line.content());
            block.setHighlight(Block.normalizeHighlight(line.highlight()));
            blocks.add(block);
        }
        if (blocks.isEmpty()) {
            blocks.add(newBlock(doc, ""));
        }
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
    }

    private SongBlock move(Integer blockId, int delta) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        List<SongBlock> blocks = new ArrayList<>(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId()));
        int idx = indexOf(blocks, blockId);
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= blocks.size()) {
            return block;
        }
        return reorder(doc, blocks, idx, target);
    }

    private SongBlock reorder(TextDocument doc, List<SongBlock> blocks, int fromIndex, int toIndex) {
        SongBlock moved = blocks.remove(fromIndex);
        blocks.add(toIndex, moved);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, blocks);
        return moved;
    }

    // --- helpers ---------------------------------------------------------

    private List<SongBlock> ensureSeeded(TextDocument doc) {
        List<SongBlock> existing = songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId());
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
                : songBlockRepository.findByTextDocumentIdAndDeletedAtIsNullOrderByOrderAsc(doc.getId());
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
            vms.add(new SongBlockViewModel(b.getId(), docId, b.getOrder(), b.getContent(), b.getHighlight()));
        }
        return vms;
    }
}
