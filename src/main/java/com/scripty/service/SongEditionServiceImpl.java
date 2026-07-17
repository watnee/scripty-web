package com.scripty.service;

import com.scripty.dto.ProjectActivity;
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.dto.TextDocument;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.SongEditionRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.song.edition.SongEditionViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Named, switchable song versions, mirroring {@link ScriptEditionServiceImpl}.
 * The container is the song's {@link TextDocument}; a version owns its own
 * {@link SongBlock} lyrics. Only the published version's lyrics are mirrored
 * onto {@link TextDocument#getContent()} — the text export / share /
 * insert-into-script read — so switching what is published re-syncs it here.
 */
@Service
public class SongEditionServiceImpl implements SongEditionService {

    public static final String DEFAULT_EDITION_NAME = "Main";

    private final SongEditionRepository songEditionRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final SongBlockRepository songBlockRepository;
    private final ProjectActivityService projectActivityService;

    @Autowired
    public SongEditionServiceImpl(SongEditionRepository songEditionRepository,
                                  TextDocumentRepository textDocumentRepository,
                                  SongBlockRepository songBlockRepository,
                                  ProjectActivityService projectActivityService) {
        this.songEditionRepository = songEditionRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.songBlockRepository = songBlockRepository;
        this.projectActivityService = projectActivityService;
    }

    @Override
    public SongEdition read(Integer id) {
        if (id == null) {
            return null;
        }
        return songEditionRepository.findById(id).orElse(null);
    }

    @Override
    public SongEdition requireForDocument(Integer documentId, Integer editionId) {
        if (documentId == null) {
            return null;
        }
        if (editionId != null) {
            SongEdition edition = songEditionRepository.findByIdAndTextDocumentId(editionId, documentId).orElse(null);
            if (edition != null) {
                return edition;
            }
        }
        return getDefaultForDocument(documentId);
    }

    @Override
    public SongEdition resolveForAccess(Integer documentId, Integer editionId, boolean canBrowseEditions) {
        if (canBrowseEditions) {
            return requireForDocument(documentId, editionId);
        }
        return getPublishedForDocument(documentId);
    }

    @Override
    public SongEdition getDefaultForDocument(Integer documentId) {
        if (documentId == null) {
            return null;
        }
        return songEditionRepository.findDefaultByTextDocumentId(documentId)
                .orElseGet(() -> songEditionRepository.findByTextDocumentIdOrderByNameAsc(documentId).stream()
                        .findFirst()
                        .orElse(null));
    }

    @Override
    public SongEdition getPublishedForDocument(Integer documentId) {
        if (documentId == null) {
            return null;
        }
        return songEditionRepository.findPublishedByTextDocumentId(documentId)
                .orElseGet(() -> getDefaultForDocument(documentId));
    }

    @Override
    @Transactional
    public SongEdition ensureDefaultEdition(Integer documentId) {
        SongEdition existing = getDefaultForDocument(documentId);
        if (existing != null) {
            if (songEditionRepository.findPublishedByTextDocumentId(documentId).isEmpty()) {
                existing.setPublished(true);
                existing.setUpdatedAt(LocalDateTime.now());
                return songEditionRepository.save(existing);
            }
            return existing;
        }
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        SongEdition edition = new SongEdition();
        edition.setTextDocument(doc);
        edition.setName(DEFAULT_EDITION_NAME);
        edition.setDefault(true);
        edition.setPublished(true);
        edition.setCreatedAt(now);
        edition.setUpdatedAt(now);
        edition.setLastEdited(doc.getUpdatedAt() != null ? doc.getUpdatedAt() : now);
        edition = songEditionRepository.save(edition);
        // Adopt any pre-edition blocks (legacy songs) into the Main version.
        adoptOrphanBlocks(doc, edition);
        return edition;
    }

    @Override
    public List<SongEdition> listForDocument(Integer documentId) {
        if (documentId == null) {
            return List.of();
        }
        return songEditionRepository.findByTextDocumentIdOrderByNameAsc(documentId);
    }

    @Override
    public List<SongEditionViewModel> getEditionViewModels(Integer documentId) {
        return getEditionViewModels(documentId, true);
    }

    @Override
    public List<SongEditionViewModel> getEditionViewModels(Integer documentId, boolean canBrowseEditions) {
        List<SongEditionViewModel> result = new ArrayList<>();
        List<SongEdition> editions = listForDocument(documentId);
        if (!canBrowseEditions) {
            SongEdition published = getPublishedForDocument(documentId);
            if (published == null) {
                return result;
            }
            editions = List.of(published);
        }
        for (SongEdition edition : editions) {
            SongEditionViewModel vm = new SongEditionViewModel();
            vm.setId(edition.getId());
            vm.setName(edition.getName());
            vm.setDefault(edition.isDefault());
            vm.setPublished(edition.isPublished());
            vm.setLastEdited(edition.getLastEdited());
            vm.setBlockCount(songBlockRepository.countBySongEditionId(edition.getId()));
            result.add(vm);
        }
        return result;
    }

    @Override
    @Transactional
    public SongEdition createEdition(Integer documentId, String name, Integer copyFromEditionId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return null;
        }

        String cleanedName = PlainTextSanitizer.sanitizeSingleLine(name);
        if (cleanedName == null || cleanedName.isBlank()) {
            cleanedName = "Untitled version";
        }
        if (cleanedName.length() > 100) {
            cleanedName = cleanedName.substring(0, 100).trim();
        }
        cleanedName = uniqueName(documentId, cleanedName);

        SongEdition source = requireForDocument(documentId, copyFromEditionId);
        if (source == null) {
            source = ensureDefaultEdition(documentId);
        }
        if (source == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        SongEdition edition = new SongEdition();
        edition.setTextDocument(doc);
        edition.setName(cleanedName);
        edition.setDefault(false);
        edition.setPublished(false);
        edition.setClonedFrom(source);
        edition.setCreatedAt(now);
        edition.setUpdatedAt(now);
        edition.setLastEdited(now);
        edition = songEditionRepository.save(edition);

        copySong(source, edition);

        Integer projectId = projectIdForDocument(doc);
        if (projectId != null) {
            projectActivityService.recordForCurrentUser(
                    projectId,
                    ProjectActivity.ACTION_DOCUMENT_UPDATED,
                    "created song version \"" + cleanedName + "\"",
                    ProjectActivity.ENTITY_DOCUMENT,
                    doc.getId());
        }

        return edition;
    }

    @Override
    @Transactional
    public boolean renameEdition(Integer editionId, Integer documentId, String name) {
        SongEdition edition = songEditionRepository.findByIdAndTextDocumentId(editionId, documentId).orElse(null);
        if (edition == null) {
            return false;
        }
        String cleanedName = PlainTextSanitizer.sanitizeSingleLine(name);
        if (cleanedName == null || cleanedName.isBlank()) {
            return false;
        }
        if (cleanedName.length() > 100) {
            cleanedName = cleanedName.substring(0, 100).trim();
        }
        if (!cleanedName.equalsIgnoreCase(edition.getName())
                && songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(documentId, cleanedName)) {
            return false;
        }
        edition.setName(cleanedName);
        edition.setUpdatedAt(LocalDateTime.now());
        songEditionRepository.save(edition);
        return true;
    }

    @Override
    @Transactional
    public boolean deleteEdition(Integer editionId, Integer documentId) {
        SongEdition edition = songEditionRepository.findByIdAndTextDocumentId(editionId, documentId).orElse(null);
        if (edition == null) {
            return false;
        }
        if (songEditionRepository.countByTextDocumentId(documentId) <= 1) {
            return false;
        }
        boolean wasDefault = edition.isDefault();
        boolean wasPublished = edition.isPublished();
        songEditionRepository.delete(edition);
        if (wasDefault || wasPublished) {
            SongEdition next = songEditionRepository.findByTextDocumentIdOrderByNameAsc(documentId).stream()
                    .findFirst()
                    .orElse(null);
            if (next != null) {
                boolean changed = false;
                if (wasDefault && !next.isDefault()) {
                    next.setDefault(true);
                    changed = true;
                }
                if (wasPublished && !next.isPublished()) {
                    next.setPublished(true);
                    changed = true;
                }
                if (changed) {
                    next.setUpdatedAt(LocalDateTime.now());
                    songEditionRepository.save(next);
                }
                if (wasPublished) {
                    resyncPublishedContent(documentId);
                }
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean setDefaultEdition(Integer editionId, Integer documentId) {
        SongEdition edition = songEditionRepository.findByIdAndTextDocumentId(editionId, documentId).orElse(null);
        if (edition == null) {
            return false;
        }
        if (edition.isDefault()) {
            return true;
        }
        for (SongEdition other : songEditionRepository.findByTextDocumentIdOrderByNameAsc(documentId)) {
            boolean shouldBeDefault = other.getId().equals(editionId);
            if (other.isDefault() != shouldBeDefault) {
                other.setDefault(shouldBeDefault);
                other.setUpdatedAt(LocalDateTime.now());
                songEditionRepository.save(other);
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean setPublishedEdition(Integer editionId, Integer documentId) {
        SongEdition edition = songEditionRepository.findByIdAndTextDocumentId(editionId, documentId).orElse(null);
        if (edition == null) {
            return false;
        }
        if (edition.isPublished()) {
            return true;
        }
        for (SongEdition other : songEditionRepository.findByTextDocumentIdOrderByNameAsc(documentId)) {
            boolean shouldBePublished = other.getId().equals(editionId);
            if (other.isPublished() != shouldBePublished) {
                other.setPublished(shouldBePublished);
                other.setUpdatedAt(LocalDateTime.now());
                songEditionRepository.save(other);
            }
        }
        // The shared document text must now mirror the newly published version.
        resyncPublishedContent(documentId);
        Integer projectId = projectIdForDocument(edition.getTextDocument());
        if (projectId != null) {
            projectActivityService.recordForCurrentUser(
                    projectId,
                    ProjectActivity.ACTION_DOCUMENT_UPDATED,
                    "shared song version \"" + edition.getName() + "\" with the team",
                    ProjectActivity.ENTITY_DOCUMENT,
                    documentId);
        }
        return true;
    }

    @Override
    @Transactional
    public void touchEdition(SongEdition edition) {
        if (edition == null || edition.getId() == null) {
            return;
        }
        SongEdition managed = songEditionRepository.findById(edition.getId()).orElse(null);
        if (managed == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        managed.setLastEdited(now);
        managed.setUpdatedAt(now);
        songEditionRepository.save(managed);
    }

    // --- helpers ---------------------------------------------------------

    private Integer projectIdForDocument(TextDocument doc) {
        return doc != null && doc.getProject() != null ? doc.getProject().getId() : null;
    }

    private String uniqueName(Integer documentId, String desired) {
        if (!songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(documentId, desired)) {
            return desired;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = desired + " (" + i + ")";
            if (candidate.length() > 100) {
                candidate = desired.substring(0, Math.max(1, 100 - (" (" + i + ")").length())) + " (" + i + ")";
            }
            if (!songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(documentId, candidate)) {
                return candidate;
            }
        }
        return desired;
    }

    /** Copies the source version's lyric blocks into the target version. */
    private void copySong(SongEdition source, SongEdition target) {
        List<SongBlock> sourceBlocks = songBlockRepository.findBySongEditionIdOrderByOrderAsc(source.getId());
        LocalDateTime now = LocalDateTime.now();
        for (SongBlock sourceBlock : sourceBlocks) {
            SongBlock copy = new SongBlock();
            copy.setTextDocument(target.getTextDocument());
            copy.setSongEdition(target);
            copy.setOrder(sourceBlock.getOrder());
            copy.setContent(sourceBlock.getContent());
            copy.setHighlight(sourceBlock.getHighlight());
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            songBlockRepository.save(copy);
        }
    }

    /**
     * Attaches any blocks created before editions existed (legacy songs whose
     * rows were backfilled by the migration, or a race) to this edition. Blocks
     * are matched by document and a null edition.
     */
    private void adoptOrphanBlocks(TextDocument doc, SongEdition edition) {
        List<SongBlock> blocks = songBlockRepository.findByTextDocumentIdOrderByOrderAsc(doc.getId());
        boolean changed = false;
        for (SongBlock block : blocks) {
            if (block.getSongEdition() == null) {
                block.setSongEdition(edition);
                changed = true;
            }
        }
        if (changed) {
            songBlockRepository.saveAll(blocks);
        }
    }

    /** Rewrites the shared document text from the published version's blocks. */
    private void resyncPublishedContent(Integer documentId) {
        TextDocument doc = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
        if (doc == null) {
            return;
        }
        SongEdition published = getPublishedForDocument(documentId);
        if (published == null) {
            return;
        }
        String content = songBlockRepository.findBySongEditionIdOrderByOrderAsc(published.getId()).stream()
                .map(b -> b.getContent() != null ? b.getContent() : "")
                .collect(Collectors.joining("\n"));
        doc.setContent(content);
        doc.setUpdatedAt(LocalDateTime.now());
        textDocumentRepository.save(doc);
    }
}
