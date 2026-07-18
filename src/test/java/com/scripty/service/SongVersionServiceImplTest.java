package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.Project;
import com.scripty.dto.SongEdition;
import com.scripty.dto.SongVersion;
import com.scripty.dto.TextDocument;
import com.scripty.repository.SongVersionRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.viewmodel.song.versionhistory.SongVersionHistoryViewModel;
import com.scripty.viewmodel.song.versionhistory.SongVersionViewModel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SongVersionServiceImplTest {

    private static final Integer DOC_ID = 42;
    private static final Integer EDITION_ID = 100;

    @Mock
    private SongVersionRepository songVersionRepository;
    @Mock
    private TextDocumentRepository textDocumentRepository;
    @Mock
    private SongBlockService songBlockService;
    @Mock
    private SongEditionService songEditionService;

    private SongVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SongVersionServiceImpl(
                songVersionRepository, textDocumentRepository, songBlockService, songEditionService,
                new ObjectMapper());
    }

    // --- snapshots --------------------------------------------------------

    @Test
    void buildSnapshotJsonCapturesTitleAndLines() {
        stubEdition("Hallelujah");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID))
                .thenReturn(List.of(line("first line"), line("second line")));

        String json = service.buildSnapshotJson(DOC_ID, EDITION_ID);

        assertTrue(json.contains("Hallelujah"));
        assertTrue(json.contains("first line"));
        assertTrue(json.contains("second line"));
    }

    @Test
    void buildSnapshotJsonReturnsNullForMissingEdition() {
        // No edition resolves for the document, so there is nothing to snapshot.
        org.junit.jupiter.api.Assertions.assertNull(service.buildSnapshotJson(DOC_ID, EDITION_ID));
    }

    // --- auto-save --------------------------------------------------------

    @Test
    void autoSaveSkipsWhenSnapshotUnchanged() {
        stubEdition("Song");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenReturn(List.of(line("same")));
        SongVersion latest = version(1, "Auto-save Jul 1, 12:00 PM", LocalDateTime.now());
        latest.setSnapshotJson(service.buildSnapshotJson(DOC_ID, EDITION_ID));
        when(songVersionRepository.findFirstBySongEditionIdOrderByCreatedAtDesc(EDITION_ID)).thenReturn(latest);

        service.autoSaveVersion(DOC_ID, EDITION_ID);

        verify(songVersionRepository, never()).save(any());
    }

    @Test
    void autoSaveCoalescesIntoRecentAutoSave() {
        stubEdition("Song");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenReturn(List.of(line("changed")));
        SongVersion latest = version(1, "Auto-save Jul 1, 12:00 PM", LocalDateTime.now().minusMinutes(2));
        latest.setSnapshotJson("{\"title\":\"Song\",\"lines\":[\"old\"]}");
        when(songVersionRepository.findFirstBySongEditionIdOrderByCreatedAtDesc(EDITION_ID)).thenReturn(latest);

        service.autoSaveVersion(DOC_ID, EDITION_ID);

        ArgumentCaptor<SongVersion> saved = ArgumentCaptor.forClass(SongVersion.class);
        verify(songVersionRepository).save(saved.capture());
        // Updated the existing row in place rather than creating a second one.
        assertEquals(1, saved.getValue().getId());
        assertTrue(saved.getValue().getSnapshotJson().contains("changed"));
    }

    @Test
    void autoSaveCreatesNewVersionWhenLatestAutoSaveIsStale() {
        stubEdition("Song");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenReturn(List.of(line("changed")));
        SongVersion latest = version(1, "Auto-save Jul 1, 12:00 PM", LocalDateTime.now().minusMinutes(30));
        latest.setSnapshotJson("{\"title\":\"Song\",\"lines\":[\"old\"]}");
        when(songVersionRepository.findFirstBySongEditionIdOrderByCreatedAtDesc(EDITION_ID)).thenReturn(latest);

        service.autoSaveVersion(DOC_ID, EDITION_ID);

        ArgumentCaptor<SongVersion> saved = ArgumentCaptor.forClass(SongVersion.class);
        verify(songVersionRepository).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getId());
    }

    @Test
    void autoSaveDoesNotCoalesceIntoManualVersion() {
        stubEdition("Song");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenReturn(List.of(line("changed")));
        SongVersion latest = version(1, "Draft 2", LocalDateTime.now().minusMinutes(1));
        latest.setSnapshotJson("{\"title\":\"Song\",\"lines\":[\"old\"]}");
        when(songVersionRepository.findFirstBySongEditionIdOrderByCreatedAtDesc(EDITION_ID)).thenReturn(latest);

        service.autoSaveVersion(DOC_ID, EDITION_ID);

        ArgumentCaptor<SongVersion> saved = ArgumentCaptor.forClass(SongVersion.class);
        verify(songVersionRepository).save(saved.capture());
        // A manual label must survive untouched, so this is a new row.
        assertEquals("Draft 2", latest.getLabel());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getId());
    }

    // --- pruning ----------------------------------------------------------

    @Test
    void pruneAutoSavesDoesNothingWhenEditionIdNull() {
        service.pruneAutoSaves(null);
        verify(songVersionRepository, never()).deleteAllById(anyList());
    }

    @Test
    void pruneAutoSavesDoesNothingWhenAtLimit() {
        when(songVersionRepository.findAutoSavesBySongEditionIdOrderByCreatedAtDesc(EDITION_ID))
                .thenReturn(autoSaves(SongVersionServiceImpl.MAX_AUTO_SAVES_PER_SONG));

        service.pruneAutoSaves(EDITION_ID);

        verify(songVersionRepository, never()).deleteAllById(anyList());
    }

    @Test
    void pruneAutoSavesDeletesOldestBeyondLimit() {
        int total = SongVersionServiceImpl.MAX_AUTO_SAVES_PER_SONG + 3;
        List<SongVersion> autos = autoSaves(total);
        when(songVersionRepository.findAutoSavesBySongEditionIdOrderByCreatedAtDesc(EDITION_ID)).thenReturn(autos);

        service.pruneAutoSaves(EDITION_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> ids = ArgumentCaptor.forClass(List.class);
        verify(songVersionRepository).deleteAllById(ids.capture());
        assertEquals(3, ids.getValue().size());
        assertEquals(
                autos.subList(SongVersionServiceImpl.MAX_AUTO_SAVES_PER_SONG, total).stream()
                        .map(SongVersion::getId)
                        .toList(),
                ids.getValue());
    }

    // --- restore / delete ownership ---------------------------------------

    @Test
    void restoreReplacesLinesAndTitleAndSnapshotsFirst() {
        stubEdition("Current title");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenReturn(List.of(line("current")));
        SongVersion version = version(9, "Draft 1", LocalDateTime.now().minusDays(1));
        version.setSnapshotJson("{\"title\":\"Old title\",\"lines\":[\"old one\",\"old two\"]}");
        when(songVersionRepository.findById(9)).thenReturn(Optional.of(version));

        assertTrue(service.restoreVersionForDocument(9, EDITION_ID));

        verify(songBlockService).replaceLines(DOC_ID, EDITION_ID, List.of(line("old one"), line("old two")));
        ArgumentCaptor<SongVersion> saved = ArgumentCaptor.forClass(SongVersion.class);
        verify(songVersionRepository).save(saved.capture());
        assertTrue(saved.getValue().getLabel().startsWith("Before restore"));
        assertTrue(saved.getValue().getSnapshotJson().contains("current"));
    }

    @Test
    void restoreRejectsVersionBelongingToAnotherEdition() {
        SongVersion version = version(9, "Draft 1", LocalDateTime.now());
        version.getSongEdition().setId(999);
        when(songVersionRepository.findById(9)).thenReturn(Optional.of(version));

        assertFalse(service.restoreVersionForDocument(9, EDITION_ID));

        verify(songBlockService, never()).replaceLines(any(), any(), anyList());
    }

    @Test
    void deleteRejectsVersionBelongingToAnotherEdition() {
        SongVersion version = version(9, "Draft 1", LocalDateTime.now());
        version.getSongEdition().setId(999);
        when(songVersionRepository.findById(9)).thenReturn(Optional.of(version));

        assertFalse(service.deleteVersionForDocument(9, EDITION_ID));

        verify(songVersionRepository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesOwnVersion() {
        SongVersion version = version(9, "Draft 1", LocalDateTime.now());
        when(songVersionRepository.findById(9)).thenReturn(Optional.of(version));

        assertTrue(service.deleteVersionForDocument(9, EDITION_ID));

        verify(songVersionRepository).deleteById(9);
    }

    // --- highlights -------------------------------------------------------

    @Test
    void snapshotRoundTripsHighlights() {
        stubEdition("Song");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID))
                .thenReturn(List.of(new SongBlockService.LineSnapshot("tinted", "YELLOW")));
        SongVersion version = version(9, "Draft 1", LocalDateTime.now().minusDays(1));
        version.setSnapshotJson(service.buildSnapshotJson(DOC_ID, EDITION_ID));
        when(songVersionRepository.findById(9)).thenReturn(Optional.of(version));

        assertTrue(service.restoreVersionForDocument(9, EDITION_ID));

        // Restoring must not drop the tint the snapshot captured.
        verify(songBlockService).replaceLines(
                DOC_ID, EDITION_ID, List.of(new SongBlockService.LineSnapshot("tinted", "YELLOW")));
    }

    @Test
    void changeSummaryReportsHighlightOnlyChange() {
        stubDocument("Song");
        stubEdition("Song");
        SongVersion newer = version(2, "Draft 2", LocalDateTime.now());
        newer.setSnapshotJson("{\"title\":\"Song\",\"lines\":[{\"content\":\"same\",\"highlight\":\"YELLOW\"}]}");
        SongVersion older = version(1, "Draft 1", LocalDateTime.now().minusDays(1));
        older.setSnapshotJson("{\"title\":\"Song\",\"lines\":[{\"content\":\"same\",\"highlight\":null}]}");
        when(songVersionRepository.findBySongEditionIdOrderByCreatedAtDesc(EDITION_ID))
                .thenReturn(List.of(newer, older));

        SongVersionViewModel newest =
                service.getVersionHistoryViewModel(DOC_ID, EDITION_ID).getVersions().get(0);

        assertEquals(1, newest.getChangeSummary().getLinesEdited());
        assertTrue(newest.getChangeSummary().getDetails().get(0).contains("highlight changed"));
    }

    @Test
    void readsLegacyStringOnlyLines() {
        stubEdition("Song");
        when(songBlockService.snapshotLines(DOC_ID, EDITION_ID)).thenReturn(List.of(line("current")));
        SongVersion version = version(9, "Draft 1", LocalDateTime.now().minusDays(1));
        // Written before songs grew highlights.
        version.setSnapshotJson("{\"title\":\"Song\",\"lines\":[\"plain\"]}");
        when(songVersionRepository.findById(9)).thenReturn(Optional.of(version));

        assertTrue(service.restoreVersionForDocument(9, EDITION_ID));

        verify(songBlockService).replaceLines(DOC_ID, EDITION_ID, List.of(line("plain")));
    }

    // --- history view model / change summary ------------------------------

    @Test
    void historySummarisesLineAndTitleChangesAgainstOlderVersion() {
        stubDocument("Song");
        stubEdition("Song");
        SongVersion newer = version(2, "Draft 2", LocalDateTime.now());
        newer.setSnapshotJson("{\"title\":\"New\",\"lines\":[\"one edited\",\"two\",\"three\"]}");
        SongVersion older = version(1, "Draft 1", LocalDateTime.now().minusDays(1));
        older.setSnapshotJson("{\"title\":\"Old\",\"lines\":[\"one\",\"two\"]}");
        when(songVersionRepository.findBySongEditionIdOrderByCreatedAtDesc(EDITION_ID))
                .thenReturn(List.of(newer, older));

        SongVersionHistoryViewModel vm = service.getVersionHistoryViewModel(DOC_ID, EDITION_ID);

        assertEquals(2, vm.getVersions().size());
        SongVersionViewModel newest = vm.getVersions().get(0);
        assertEquals(3, newest.getLineCount());
        assertEquals(1, newest.getChangeSummary().getLinesEdited());
        assertEquals(1, newest.getChangeSummary().getLinesAdded());
        assertTrue(newest.getChangeSummary().isTitleChanged());

        // The oldest version has nothing to compare against.
        assertEquals("Initial saved state", vm.getVersions().get(1).getChangeSummary().getDetails().get(0));
    }

    @Test
    void historyFlagsAutoSavesAndSurvivesUnreadableSnapshot() {
        stubDocument("Song");
        stubEdition("Song");
        SongVersion broken = version(3, "Auto-save Jul 1, 12:00 PM", LocalDateTime.now());
        broken.setSnapshotJson("not json");
        when(songVersionRepository.findBySongEditionIdOrderByCreatedAtDesc(EDITION_ID))
                .thenReturn(List.of(broken));

        SongVersionHistoryViewModel vm = service.getVersionHistoryViewModel(DOC_ID, EDITION_ID);

        SongVersionViewModel only = vm.getVersions().get(0);
        assertTrue(only.isAutoSave());
        assertEquals(0, only.getLineCount());
        assertEquals("Unable to read snapshot", only.getChangeSummary().getDetails().get(0));
    }

    @Test
    void historyForMissingDocumentIsEmpty() {
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.empty());

        SongVersionHistoryViewModel vm = service.getVersionHistoryViewModel(DOC_ID, EDITION_ID);

        assertTrue(vm.getVersions().isEmpty());
        assertEquals(DOC_ID, vm.getDocumentId());
    }

    // --- helpers ----------------------------------------------------------

    private static SongBlockService.LineSnapshot line(String content) {
        return new SongBlockService.LineSnapshot(content, null);
    }

    /** Stubs the document lookup used by the version-history read path. */
    private void stubDocument(String title) {
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(document(title)));
    }

    /** Stubs edition resolution to a default+published version of the song. */
    private SongEdition stubEdition(String title) {
        SongEdition edition = edition(title);
        when(songEditionService.requireForDocument(eq(DOC_ID), any())).thenReturn(edition);
        return edition;
    }

    private static TextDocument document(String title) {
        Project project = new Project();
        project.setId(7);
        project.setTitle("Project");
        TextDocument doc = new TextDocument();
        doc.setId(DOC_ID);
        doc.setTitle(title);
        doc.setProject(project);
        return doc;
    }

    private static SongEdition edition(String title) {
        SongEdition edition = new SongEdition();
        edition.setId(EDITION_ID);
        edition.setName("Main");
        edition.setDefault(true);
        edition.setPublished(true);
        edition.setTextDocument(document(title));
        return edition;
    }

    private static SongVersion version(Integer id, String label, LocalDateTime createdAt) {
        SongVersion v = new SongVersion();
        v.setId(id);
        v.setLabel(label);
        v.setCreatedAt(createdAt);
        v.setSnapshotJson("{}");
        v.setTextDocument(document("Song"));
        v.setSongEdition(edition("Song"));
        return v;
    }

    private static List<SongVersion> autoSaves(int count) {
        List<SongVersion> list = new ArrayList<>(count);
        LocalDateTime base = LocalDateTime.of(2026, 7, 1, 12, 0);
        for (int i = 0; i < count; i++) {
            list.add(version(1000 + i, "Auto-save Jul 1, 12:00 PM", base.minusMinutes(i)));
        }
        return list;
    }
}
