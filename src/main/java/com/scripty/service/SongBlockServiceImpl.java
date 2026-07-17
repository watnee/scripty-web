package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
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
    private final SongEditionService songEditionService;

    @Autowired
    public SongBlockServiceImpl(SongBlockRepository songBlockRepository,
                                TextDocumentRepository textDocumentRepository,
                                ProjectRepository projectRepository,
                                SongEditionService songEditionService) {
        this.songBlockRepository = songBlockRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.projectRepository = projectRepository;
        this.songEditionService = songEditionService;
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
    public Integer editionIdForBlock(Integer blockId) {
        SongBlock block = read(blockId);
        if (block == null) {
            return null;
        }
        SongEdition edition = resolveEditionForBlock(block);
        return edition != null ? edition.getId() : null;
    }

    @Override
    @Transactional
    public List<SongBlockViewModel> getBlocks(Integer documentId, Integer editionId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return List.of();
        }
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null) {
            return List.of();
        }
        List<SongBlock> blocks = ensureSeeded(doc, edition);
        return toViewModels(blocks);
    }

    @Override
    @Transactional
    public SongBlock appendBlock(Integer documentId, Integer editionId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return null;
        }
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null) {
            return null;
        }
        List<SongBlock> blocks = new ArrayList<>(ensureSeeded(doc, edition));
        SongBlock created = newBlock(doc, edition, "");
        blocks.add(created);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, edition, blocks);
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
        SongEdition edition = resolveEditionForBlock(after);
        if (afterContent != null) {
            after.setContent(PlainTextSanitizer.sanitize(afterContent));
            after.setUpdatedAt(LocalDateTime.now());
        }
        List<SongBlock> blocks = new ArrayList<>(blocksFor(edition));
        int idx = indexOf(blocks, afterBlockId);
        SongBlock created = newBlock(doc, edition, "");
        blocks.add(idx + 1, created);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, edition, blocks);
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
        rebuildDocumentContent(block.getTextDocument(), resolveEditionForBlock(block), null);
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
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        SongEdition edition = resolveEditionForBlock(block);
        List<SongBlock> blocks = new ArrayList<>(blocksFor(edition));
        blocks.removeIf(b -> b.getId().equals(blockId));
        songBlockRepository.delete(block);
        if (blocks.isEmpty()) {
            blocks.add(newBlock(doc, edition, ""));
        }
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, edition, blocks);
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

    @Override
    @Transactional
    public SongBlock moveTo(Integer blockId, int position) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        SongEdition edition = resolveEditionForBlock(block);
        List<SongBlock> blocks = new ArrayList<>(blocksFor(edition));
        int idx = indexOf(blocks, blockId);
        if (idx < 0) {
            return block;
        }
        int target = Math.max(0, Math.min(position, blocks.size() - 1));
        if (target == idx) {
            return block;
        }
        return reorder(doc, edition, blocks, idx, target);
    }

    @Override
    @Transactional
    public List<LineSnapshot> snapshotLines(Integer documentId, Integer editionId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return null;
        }
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null) {
            return null;
        }
        return ensureSeeded(doc, edition).stream()
                .map(b -> new LineSnapshot(b.getContent() != null ? b.getContent() : "", b.getHighlight()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void replaceLines(Integer documentId, Integer editionId, List<LineSnapshot> lines) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null || lines == null) {
            return;
        }
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null) {
            return;
        }
        songBlockRepository.deleteAll(blocksFor(edition));
        // Hibernate orders inserts before deletes at flush time; force the deletes
        // out first so a later read of this version does not see the old rows.
        songBlockRepository.flush();
        List<SongBlock> blocks = new ArrayList<>();
        for (LineSnapshot line : lines) {
            SongBlock block = newBlock(doc, edition, line.content());
            block.setHighlight(Block.normalizeHighlight(line.highlight()));
            blocks.add(block);
        }
        if (blocks.isEmpty()) {
            blocks.add(newBlock(doc, edition, ""));
        }
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, edition, blocks);
    }

    private SongBlock move(Integer blockId, int delta) {
        SongBlock block = read(blockId);
        if (block == null || block.getTextDocument() == null) {
            return null;
        }
        TextDocument doc = block.getTextDocument();
        SongEdition edition = resolveEditionForBlock(block);
        List<SongBlock> blocks = new ArrayList<>(blocksFor(edition));
        int idx = indexOf(blocks, blockId);
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= blocks.size()) {
            return block;
        }
        return reorder(doc, edition, blocks, idx, target);
    }

    private SongBlock reorder(TextDocument doc, SongEdition edition, List<SongBlock> blocks, int fromIndex, int toIndex) {
        SongBlock moved = blocks.remove(fromIndex);
        blocks.add(toIndex, moved);
        renumberAndSave(blocks);
        rebuildDocumentContent(doc, edition, blocks);
        return moved;
    }

    // --- helpers ---------------------------------------------------------

    /** Resolves the requested version of a song, falling back to its default. */
    private SongEdition resolveEdition(Integer documentId, Integer editionId) {
        SongEdition edition = songEditionService.requireForDocument(documentId, editionId);
        if (edition == null) {
            edition = songEditionService.ensureDefaultEdition(documentId);
        }
        return edition;
    }

    /** The version a block belongs to, healing legacy blocks with no version. */
    private SongEdition resolveEditionForBlock(SongBlock block) {
        if (block.getSongEdition() != null) {
            return block.getSongEdition();
        }
        TextDocument doc = block.getTextDocument();
        if (doc == null) {
            return null;
        }
        SongEdition edition = songEditionService.ensureDefaultEdition(doc.getId());
        if (edition != null) {
            block.setSongEdition(edition);
            songBlockRepository.save(block);
        }
        return edition;
    }

    private List<SongBlock> blocksFor(SongEdition edition) {
        if (edition == null) {
            return List.of();
        }
        return songBlockRepository.findBySongEditionIdOrderByOrderAsc(edition.getId());
    }

    private List<SongBlock> ensureSeeded(TextDocument doc, SongEdition edition) {
        List<SongBlock> existing = songBlockRepository.findBySongEditionIdOrderByOrderAsc(edition.getId());
        if (!existing.isEmpty()) {
            return existing;
        }
        List<SongBlock> seeded = new ArrayList<>();
        // Only the published version mirrors the document text; other versions
        // must never inherit it, or they would silently copy the published lyrics.
        if (edition.isPublished()) {
            for (String line : splitContentIntoLines(doc.getContent())) {
                seeded.add(newBlock(doc, edition, line));
            }
        }
        if (seeded.isEmpty()) {
            seeded.add(newBlock(doc, edition, ""));
        }
        renumberAndSave(seeded);
        // Keep content byte-identical to the block join so nothing drifts.
        rebuildDocumentContent(doc, edition, seeded);
        return seeded;
    }

    private SongBlock newBlock(TextDocument doc, SongEdition edition, String content) {
        LocalDateTime now = LocalDateTime.now();
        SongBlock block = new SongBlock();
        block.setTextDocument(doc);
        block.setSongEdition(edition);
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

    /**
     * Stamps the version's and project's edit times on every change, but only
     * rewrites the shared {@link TextDocument#getContent()} when the version is
     * the published one — that field feeds export/share/insert-into-script.
     */
    private void rebuildDocumentContent(TextDocument doc, SongEdition edition, List<SongBlock> blocks) {
        LocalDateTime now = LocalDateTime.now();
        // touchEdition reloads by id and stamps lastEdited/updatedAt, avoiding
        // LazyInitializationException when a bulk renumber detached the proxy.
        songEditionService.touchEdition(edition);
        Project project = doc.getProject();
        if (project != null) {
            project.setLastEdited(now);
            projectRepository.save(project);
        }
        if (edition == null || !edition.isPublished()) {
            return;
        }
        List<SongBlock> ordered = blocks != null
                ? blocks
                : songBlockRepository.findBySongEditionIdOrderByOrderAsc(edition.getId());
        String content = ordered.stream()
                .map(b -> b.getContent() != null ? b.getContent() : "")
                .collect(Collectors.joining("\n"));
        doc.setContent(content);
        doc.setUpdatedAt(now);
        textDocumentRepository.save(doc);
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
