package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.SongEdition;
import com.scripty.dto.SongVersion;
import com.scripty.dto.TextDocument;
import com.scripty.repository.SongVersionRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.util.PlainTextSanitizer;
import com.scripty.viewmodel.song.versionhistory.SongVersionChangeSummary;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import com.scripty.viewmodel.song.versionhistory.SongVersionViewModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SongVersionServiceImpl implements SongVersionService {

    private static final int AUTO_SAVE_INTERVAL_MINUTES = 10;
    /** Newest auto-saves kept per song version; manual / before-restore labels are never pruned. */
    static final int MAX_AUTO_SAVES_PER_SONG = 30;
    private static final DateTimeFormatter AUTO_SAVE_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final SongVersionRepository songVersionRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final SongBlockService songBlockService;
    private final SongEditionService songEditionService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SongVersionServiceImpl(SongVersionRepository songVersionRepository,
                                  TextDocumentRepository textDocumentRepository,
                                  SongBlockService songBlockService,
                                  SongEditionService songEditionService,
                                  ObjectMapper objectMapper) {
        this.songVersionRepository = songVersionRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.songBlockService = songBlockService;
        this.songEditionService = songEditionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public SongVersionHistoryViewModel getVersionHistoryViewModel(Integer documentId, Integer editionId) {
        SongVersionHistoryViewModel vm = new SongVersionHistoryViewModel();
        TextDocument doc = documentId != null
                ? textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null)
                : null;
        if (doc == null) {
            vm.setDocumentId(documentId != null ? documentId : 0);
            vm.setVersions(List.of());
            return vm;
        }
        SongEdition edition = songEditionService.requireForDocument(documentId, editionId);
        if (edition == null) {
            edition = songEditionService.ensureDefaultEdition(documentId);
        }

        vm.setDocumentId(doc.getId());
        vm.setSongTitle(doc.getTitle());
        if (edition != null) {
            vm.setEditionId(edition.getId());
            vm.setEditionName(edition.getName());
        }
        if (doc.getProject() != null) {
            vm.setProjectId(doc.getProject().getId());
            vm.setProjectTitle(doc.getProject().getTitle());
        }

        List<SongVersion> versions = edition != null
                ? songVersionRepository.findBySongEditionIdOrderByCreatedAtDesc(edition.getId())
                : List.of();
        List<SongVersionViewModel> versionVMs = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            SongVersion version = versions.get(i);
            SongVersionViewModel vvm = new SongVersionViewModel();
            vvm.setId(version.getId());
            vvm.setLabel(version.getLabel());
            vvm.setCreatedAt(version.getCreatedAt());
            vvm.setAutoSave(isAutoSaveLabel(version.getLabel()));

            Map<String, Object> snapshot = readSnapshot(version.getSnapshotJson());
            if (snapshot == null) {
                vvm.setLineCount(0);
                SongVersionChangeSummary broken = new SongVersionChangeSummary();
                broken.addDetail("Unable to read snapshot");
                vvm.setChangeSummary(broken);
            } else {
                vvm.setTitle(titleOf(snapshot));
                vvm.setLineCount(linesOf(snapshot).size());
                Map<String, Object> older = i + 1 < versions.size()
                        ? readSnapshot(versions.get(i + 1).getSnapshotJson())
                        : null;
                vvm.setChangeSummary(computeChangeSummary(snapshot, older));
            }
            versionVMs.add(vvm);
        }
        vm.setVersions(versionVMs);
        return vm;
    }

    @Override
    @Transactional
    public SongVersion createVersion(Integer documentId, Integer editionId, String label) {
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null) {
            return null;
        }
        return createVersionFromSnapshot(edition, label, buildSnapshotJson(documentId, edition.getId()));
    }

    private SongVersion createVersionFromSnapshot(SongEdition edition, String label, String snapshotJson) {
        if (edition == null || edition.getTextDocument() == null || snapshotJson == null) {
            return null;
        }
        SongVersion version = new SongVersion();
        version.setTextDocument(edition.getTextDocument());
        version.setSongEdition(edition);
        version.setLabel(label);
        version.setSnapshotJson(snapshotJson);
        version.setCreatedAt(LocalDateTime.now());
        return songVersionRepository.save(version);
    }

    @Override
    @Transactional
    public String buildSnapshotJson(Integer documentId, Integer editionId) {
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null || edition.getTextDocument() == null) {
            return null;
        }
        List<SongBlockService.LineSnapshot> lines = songBlockService.snapshotLines(documentId, edition.getId());
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("title", edition.getTextDocument().getTitle());
        snapshot.put("lines", lines != null ? lines : List.of());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize song snapshot", e);
        }
    }

    @Override
    @Transactional
    public void autoSaveVersion(Integer documentId, Integer editionId) {
        SongEdition edition = resolveEdition(documentId, editionId);
        if (edition == null) {
            return;
        }
        String snapshotJson = buildSnapshotJson(documentId, edition.getId());
        if (snapshotJson == null) {
            return;
        }
        SongVersion latest = songVersionRepository.findFirstBySongEditionIdOrderByCreatedAtDesc(edition.getId());
        if (latest != null && snapshotJson.equals(latest.getSnapshotJson())) {
            return;
        }
        String label = "Auto-save " + LocalDateTime.now().format(AUTO_SAVE_LABEL_FORMAT);
        if (latest != null
                && isAutoSaveLabel(latest.getLabel())
                && latest.getCreatedAt().plusMinutes(AUTO_SAVE_INTERVAL_MINUTES).isAfter(LocalDateTime.now())) {
            latest.setSnapshotJson(snapshotJson);
            latest.setCreatedAt(LocalDateTime.now());
            latest.setLabel(label);
            songVersionRepository.save(latest);
        } else {
            createVersionFromSnapshot(edition, label, snapshotJson);
        }
        pruneAutoSaves(edition.getId());
    }

    @Override
    @Transactional
    public void autoSaveVersionForBlock(Integer blockId) {
        Integer documentId = songBlockService.documentIdForBlock(blockId);
        Integer editionId = songBlockService.editionIdForBlock(blockId);
        if (documentId == null || editionId == null) {
            return;
        }
        autoSaveVersion(documentId, editionId);
    }

    /**
     * Keeps the newest {@link #MAX_AUTO_SAVES_PER_SONG} auto-saves for a song
     * version. Manual labels and "Before restore …" entries are never deleted here.
     */
    void pruneAutoSaves(Integer editionId) {
        if (editionId == null) {
            return;
        }
        List<SongVersion> autoSaves =
                songVersionRepository.findAutoSavesBySongEditionIdOrderByCreatedAtDesc(editionId);
        if (autoSaves.size() <= MAX_AUTO_SAVES_PER_SONG) {
            return;
        }
        List<Integer> toDelete = autoSaves.stream()
                .skip(MAX_AUTO_SAVES_PER_SONG)
                .map(SongVersion::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!toDelete.isEmpty()) {
            songVersionRepository.deleteAllById(toDelete);
        }
    }

    @Override
    @Transactional
    public boolean restoreVersionForDocument(Integer versionId, Integer editionId) {
        SongVersion version = versionId != null
                ? songVersionRepository.findById(versionId).orElse(null)
                : null;
        if (version == null || version.getSongEdition() == null || editionId == null
                || !editionId.equals(version.getSongEdition().getId())) {
            return false;
        }
        SongEdition edition = version.getSongEdition();
        TextDocument doc = edition.getTextDocument();
        if (doc == null) {
            return false;
        }
        Integer documentId = doc.getId();
        Map<String, Object> snapshot = readSnapshot(version.getSnapshotJson());
        if (snapshot == null) {
            return false;
        }

        String beforeLabel = "Before restore " + LocalDateTime.now().format(AUTO_SAVE_LABEL_FORMAT);
        createVersionFromSnapshot(edition, beforeLabel, buildSnapshotJson(documentId, editionId));

        // replaceLines rebuilds the version's content (and, when published, the
        // document text) and stamps updatedAt.
        songBlockService.replaceLines(documentId, editionId, linesOf(snapshot));
        // The title lives on the shared document, so only a published version's
        // restore may rename the song.
        if (edition.isPublished()) {
            TextDocument managed = textDocumentRepository.findByIdAndDeletedAtIsNull(documentId).orElse(null);
            if (managed != null) {
                String title = titleOf(snapshot);
                if (title != null && !title.isBlank()) {
                    managed.setTitle(PlainTextSanitizer.sanitizeSingleLine(title));
                    textDocumentRepository.save(managed);
                }
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean deleteVersionForDocument(Integer versionId, Integer editionId) {
        SongVersion version = versionId != null
                ? songVersionRepository.findById(versionId).orElse(null)
                : null;
        if (version == null || version.getSongEdition() == null || editionId == null
                || !editionId.equals(version.getSongEdition().getId())) {
            return false;
        }
        songVersionRepository.deleteById(versionId);
        return true;
    }

    // --- helpers ---------------------------------------------------------

    private SongEdition resolveEdition(Integer documentId, Integer editionId) {
        SongEdition edition = songEditionService.requireForDocument(documentId, editionId);
        if (edition == null) {
            edition = songEditionService.ensureDefaultEdition(documentId);
        }
        return edition;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String titleOf(Map<String, Object> snapshot) {
        Object title = snapshot.get("title");
        return title != null ? title.toString() : null;
    }

    /**
     * Lines from a snapshot. Entries are {@code {content, highlight}} objects;
     * bare strings are also accepted, since snapshots written before songs grew
     * highlights stored the content alone.
     */
    private static List<SongBlockService.LineSnapshot> linesOf(Map<String, Object> snapshot) {
        Object lines = snapshot.get("lines");
        if (!(lines instanceof List<?> list)) {
            return List.of();
        }
        List<SongBlockService.LineSnapshot> result = new ArrayList<>();
        for (Object line : list) {
            if (line instanceof Map<?, ?> map) {
                Object content = map.get("content");
                Object highlight = map.get("highlight");
                result.add(new SongBlockService.LineSnapshot(
                        content != null ? content.toString() : "",
                        highlight != null ? highlight.toString() : null));
            } else {
                result.add(new SongBlockService.LineSnapshot(line != null ? line.toString() : "", null));
            }
        }
        return result;
    }

    private static boolean isAutoSaveLabel(String label) {
        return label != null && label.startsWith("Auto-save");
    }

    /**
     * Diffs lines by position, matching how the screenplay diffs blocks by order.
     * An inserted line therefore reads as an edit plus an addition rather than a
     * shift, which keeps the summary cheap and predictable.
     */
    private SongVersionChangeSummary computeChangeSummary(Map<String, Object> newer, Map<String, Object> older) {
        SongVersionChangeSummary summary = new SongVersionChangeSummary();
        if (older == null) {
            summary.addDetail("Initial saved state");
            return summary;
        }

        if (!Objects.equals(titleOf(newer), titleOf(older))) {
            summary.setTitleChanged(true);
            summary.addDetail("Title changed: " + titleLabel(titleOf(older)) + " → " + titleLabel(titleOf(newer)));
        }

        List<SongBlockService.LineSnapshot> newerLines = linesOf(newer);
        List<SongBlockService.LineSnapshot> olderLines = linesOf(older);
        int shared = Math.min(newerLines.size(), olderLines.size());
        for (int i = 0; i < shared; i++) {
            SongBlockService.LineSnapshot newerLine = newerLines.get(i);
            SongBlockService.LineSnapshot olderLine = olderLines.get(i);
            if (!Objects.equals(newerLine.content(), olderLine.content())) {
                summary.setLinesEdited(summary.getLinesEdited() + 1);
                summary.addDetail("Line " + (i + 1) + " edited: " + linePreview(newerLine.content()));
            } else if (!Objects.equals(newerLine.highlight(), olderLine.highlight())) {
                summary.setLinesEdited(summary.getLinesEdited() + 1);
                summary.addDetail("Line " + (i + 1) + " highlight changed: " + linePreview(newerLine.content()));
            }
        }
        for (int i = shared; i < newerLines.size(); i++) {
            summary.setLinesAdded(summary.getLinesAdded() + 1);
            summary.addDetail("Line added: " + linePreview(newerLines.get(i).content()));
        }
        for (int i = shared; i < olderLines.size(); i++) {
            summary.setLinesRemoved(summary.getLinesRemoved() + 1);
            summary.addDetail("Line removed: " + linePreview(olderLines.get(i).content()));
        }
        return summary;
    }

    private static String titleLabel(String title) {
        return title != null && !title.isBlank() ? "\"" + title + "\"" : "(untitled)";
    }

    private static String linePreview(String line) {
        if (line == null || line.isBlank()) {
            return "(empty)";
        }
        String text = line.replaceAll("\\s+", " ").trim();
        if (text.length() <= 48) {
            return "\"" + text + "\"";
        }
        return "\"" + text.substring(0, 45) + "...\"";
    }
}
