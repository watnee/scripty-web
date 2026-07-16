package com.scripty.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    /** Newest auto-saves kept per song; manual / before-restore labels are never pruned. */
    static final int MAX_AUTO_SAVES_PER_SONG = 30;
    private static final DateTimeFormatter AUTO_SAVE_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final SongVersionRepository songVersionRepository;
    private final TextDocumentRepository textDocumentRepository;
    private final SongBlockService songBlockService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SongVersionServiceImpl(SongVersionRepository songVersionRepository,
                                  TextDocumentRepository textDocumentRepository,
                                  SongBlockService songBlockService,
                                  ObjectMapper objectMapper) {
        this.songVersionRepository = songVersionRepository;
        this.textDocumentRepository = textDocumentRepository;
        this.songBlockService = songBlockService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public SongVersionHistoryViewModel getVersionHistoryViewModel(Integer documentId) {
        SongVersionHistoryViewModel vm = new SongVersionHistoryViewModel();
        TextDocument doc = documentId != null
                ? textDocumentRepository.findById(documentId).orElse(null)
                : null;
        if (doc == null) {
            vm.setDocumentId(documentId != null ? documentId : 0);
            vm.setVersions(List.of());
            return vm;
        }

        vm.setDocumentId(doc.getId());
        vm.setSongTitle(doc.getTitle());
        if (doc.getProject() != null) {
            vm.setProjectId(doc.getProject().getId());
            vm.setProjectTitle(doc.getProject().getTitle());
        }

        List<SongVersion> versions = songVersionRepository.findByTextDocumentIdOrderByCreatedAtDesc(doc.getId());
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
    public SongVersion createVersion(Integer documentId, String label) {
        return createVersionFromSnapshot(documentId, label, buildSnapshotJson(documentId));
    }

    private SongVersion createVersionFromSnapshot(Integer documentId, String label, String snapshotJson) {
        TextDocument doc = documentId != null
                ? textDocumentRepository.findById(documentId).orElse(null)
                : null;
        if (doc == null || snapshotJson == null) {
            return null;
        }
        SongVersion version = new SongVersion();
        version.setTextDocument(doc);
        version.setLabel(label);
        version.setSnapshotJson(snapshotJson);
        version.setCreatedAt(LocalDateTime.now());
        return songVersionRepository.save(version);
    }

    @Override
    @Transactional
    public String buildSnapshotJson(Integer documentId) {
        TextDocument doc = documentId != null
                ? textDocumentRepository.findById(documentId).orElse(null)
                : null;
        if (doc == null) {
            return null;
        }
        List<String> lines = songBlockService.snapshotLines(documentId);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("title", doc.getTitle());
        snapshot.put("lines", lines != null ? lines : List.of());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize song snapshot", e);
        }
    }

    @Override
    @Transactional
    public void autoSaveVersion(Integer documentId) {
        String snapshotJson = buildSnapshotJson(documentId);
        if (snapshotJson == null) {
            return;
        }
        SongVersion latest = songVersionRepository.findFirstByTextDocumentIdOrderByCreatedAtDesc(documentId);
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
            createVersionFromSnapshot(documentId, label, snapshotJson);
        }
        pruneAutoSaves(documentId);
    }

    @Override
    @Transactional
    public void autoSaveVersionForBlock(Integer blockId) {
        autoSaveVersion(songBlockService.documentIdForBlock(blockId));
    }

    /**
     * Keeps the newest {@link #MAX_AUTO_SAVES_PER_SONG} auto-saves for a song.
     * Manual labels and "Before restore …" entries are never deleted here.
     */
    void pruneAutoSaves(Integer documentId) {
        if (documentId == null) {
            return;
        }
        List<SongVersion> autoSaves =
                songVersionRepository.findAutoSavesByTextDocumentIdOrderByCreatedAtDesc(documentId);
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
    public boolean restoreVersionForDocument(Integer versionId, Integer documentId) {
        SongVersion version = versionId != null
                ? songVersionRepository.findById(versionId).orElse(null)
                : null;
        if (version == null || version.getTextDocument() == null || documentId == null
                || !documentId.equals(version.getTextDocument().getId())) {
            return false;
        }
        Map<String, Object> snapshot = readSnapshot(version.getSnapshotJson());
        if (snapshot == null) {
            return false;
        }

        String beforeLabel = "Before restore " + LocalDateTime.now().format(AUTO_SAVE_LABEL_FORMAT);
        createVersionFromSnapshot(documentId, beforeLabel, buildSnapshotJson(documentId));

        // replaceLines rebuilds the document content and stamps updatedAt, so the
        // title is saved through the same document instance it loads.
        songBlockService.replaceLines(documentId, linesOf(snapshot));
        TextDocument doc = textDocumentRepository.findById(documentId).orElse(null);
        if (doc != null) {
            String title = titleOf(snapshot);
            if (title != null && !title.isBlank()) {
                doc.setTitle(PlainTextSanitizer.sanitizeSingleLine(title));
                textDocumentRepository.save(doc);
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean deleteVersionForDocument(Integer versionId, Integer documentId) {
        SongVersion version = versionId != null
                ? songVersionRepository.findById(versionId).orElse(null)
                : null;
        if (version == null || version.getTextDocument() == null || documentId == null
                || !documentId.equals(version.getTextDocument().getId())) {
            return false;
        }
        songVersionRepository.deleteById(versionId);
        return true;
    }

    // --- snapshot helpers -------------------------------------------------

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

    @SuppressWarnings("unchecked")
    private static List<String> linesOf(Map<String, Object> snapshot) {
        Object lines = snapshot.get("lines");
        if (!(lines instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object line : list) {
            result.add(line != null ? line.toString() : "");
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

        List<String> newerLines = linesOf(newer);
        List<String> olderLines = linesOf(older);
        int shared = Math.min(newerLines.size(), olderLines.size());
        for (int i = 0; i < shared; i++) {
            if (!Objects.equals(newerLines.get(i), olderLines.get(i))) {
                summary.setLinesEdited(summary.getLinesEdited() + 1);
                summary.addDetail("Line " + (i + 1) + " edited: " + linePreview(newerLines.get(i)));
            }
        }
        for (int i = shared; i < newerLines.size(); i++) {
            summary.setLinesAdded(summary.getLinesAdded() + 1);
            summary.addDetail("Line added: " + linePreview(newerLines.get(i)));
        }
        for (int i = shared; i < olderLines.size(); i++) {
            summary.setLinesRemoved(summary.getLinesRemoved() + 1);
            summary.addDetail("Line removed: " + linePreview(olderLines.get(i)));
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
