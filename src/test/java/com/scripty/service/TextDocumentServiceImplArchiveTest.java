package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentFolderRepository;
import com.scripty.repository.TextDocumentRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the archive → unarchive lifecycle, and the ways it must stay distinct
 * from the trash: nothing expires, the row is never removed, and an archived
 * document is still whole.
 */
class TextDocumentServiceImplArchiveTest {

    private static final int PROJECT_ID = 7;
    private static final int SONG_ID = 42;
    private static final int NOTE_ID = 43;

    private TextDocumentRepository textDocumentRepository;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ProjectActivityService projectActivityService;
    private TextDocumentServiceImpl service;

    private Project project;
    private User user;
    private TextDocument song;
    private TextDocument note;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        projectActivityService = mock(ProjectActivityService.class);
        service = new TextDocumentServiceImpl(
                textDocumentRepository,
                mock(TextDocumentFolderRepository.class),
                projectRepository,
                mock(BlockRepository.class),
                mock(BlockService.class),
                projectService,
                mock(ScriptImportTextExtractor.class),
                projectActivityService,
                mock(ScriptEditionService.class),
                mock(EmailService.class));
        ReflectionTestUtils.setField(service, "trashRetentionDays", 30);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("The Big Musical");

        user = new User();
        user.setId(3);
        user.setUsername("writer");

        song = new TextDocument();
        song.setId(SONG_ID);
        song.setProject(project);
        song.setDocumentType(TextDocument.TYPE_SONG);
        song.setTitle("Opening Number");
        song.setSortOrder(0);

        note = new TextDocument();
        note.setId(NOTE_ID);
        note.setProject(project);
        note.setDocumentType(TextDocument.TYPE_NOTES);
        note.setTitle("Scene ideas");
        note.setSortOrder(1);

        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(true);
        when(textDocumentRepository.save(any(TextDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void archiveStampsTheDocumentWithoutTrashingOrRemovingIt() {
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));

        TextDocument archived = service.archive(SONG_ID, PROJECT_ID, user);

        assertNotNull(archived);
        assertTrue(song.isArchived(), "archive should stamp archived_at");
        assertFalse(song.isDeleted(), "archiving is not a delete");
        verify(textDocumentRepository).save(song);
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
        verify(projectActivityService).record(
                eq(PROJECT_ID),
                eq(user.getId()),
                eq(ProjectActivity.ACTION_DOCUMENT_ARCHIVED),
                contains("archived"),
                eq(ProjectActivity.ENTITY_DOCUMENT),
                eq(SONG_ID));
    }

    @Test
    void archiveAcceptsANoteAsReadilyAsASong() {
        // Unlike deleteDocuments, archiving does nothing type-specific — the bulk
        // rel is advertised on projects of notes for exactly this reason.
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(NOTE_ID, PROJECT_ID))
                .thenReturn(Optional.of(note));

        assertNotNull(service.archive(NOTE_ID, PROJECT_ID, user));
        assertTrue(note.isArchived());
    }

    @Test
    void archiveRejectsSomethingAlreadyArchivedOrInTheTrash() {
        // The finder is the guard: it asks for archived_at IS NULL AND
        // deleted_at IS NULL, so neither state can be archived twice.
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertNull(service.archive(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
    }

    @Test
    void archiveIsIgnoredWithoutProjectAccess() {
        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(false);

        assertNull(service.archive(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
    }

    @Test
    void unarchiveClearsTheStampAndSendsTheDocumentToTheEndOfTheList() {
        song.setArchivedAt(LocalDateTime.now().minusDays(9));
        song.setSortOrder(99);
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));
        when(textDocumentRepository.countByProjectIdAndDeletedAtIsNull(PROJECT_ID)).thenReturn(4);

        TextDocument restored = service.unarchive(SONG_ID, PROJECT_ID, user);

        assertNotNull(restored);
        assertFalse(restored.isArchived());
        assertEquals(4, restored.getSortOrder(), "unarchived document should land at the end of the list");
        verify(projectActivityService).record(
                eq(PROJECT_ID),
                eq(user.getId()),
                eq(ProjectActivity.ACTION_DOCUMENT_UNARCHIVED),
                contains("archive"),
                eq(ProjectActivity.ENTITY_DOCUMENT),
                eq(SONG_ID));
    }

    @Test
    void unarchiveRejectsADocumentThatIsNotArchived() {
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertNull(service.unarchive(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
    }

    @Test
    void restoringFromTheTrashClearsTheArchiveStampToo() {
        // Something archived and then deleted must come back where the writer is
        // looking, not vanish again into the archive.
        song.setArchivedAt(LocalDateTime.now().minusDays(9));
        song.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNotNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));

        TextDocument restored = service.restore(SONG_ID, PROJECT_ID, user);

        assertNotNull(restored);
        assertFalse(restored.isDeleted());
        assertFalse(restored.isArchived(), "restore should bring it back into the list, not the archive");
    }

    @Test
    void archiveDocumentsSkipsIdsItCannotArchiveAndDeduplicates() {
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(NOTE_ID, PROJECT_ID))
                .thenReturn(Optional.of(note));
        // 99 is another project's, or already archived; either way the finder is empty.
        when(textDocumentRepository.findByIdAndProjectIdAndArchivedAtIsNullAndDeletedAtIsNull(99, PROJECT_ID))
                .thenReturn(Optional.empty());

        // Arrays.asList, not List.of: the production path can carry a null id
        // from Jackson, and List.of throws on it.
        int archived = service.archiveDocuments(
                Arrays.asList(SONG_ID, NOTE_ID, SONG_ID, 99, null), PROJECT_ID, user);

        assertEquals(2, archived, "a repeated id counts once and an unknown one not at all");
        assertTrue(song.isArchived());
        assertTrue(note.isArchived());
    }

    @Test
    void archiveDocumentsIsIgnoredWithoutProjectAccess() {
        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(false);

        assertEquals(0, service.archiveDocuments(List.of(SONG_ID), PROJECT_ID, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
    }

    @Test
    void archiveViewModelSplitsByTypeAndCarriesTheArchiveDate() {
        LocalDateTime archivedAt = LocalDateTime.now().minusDays(9);
        song.setArchivedAt(archivedAt);
        note.setArchivedAt(archivedAt);

        when(projectRepository.findWithTeamsById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, user)).thenReturn(true);
        when(textDocumentRepository
                .findByProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNullOrderByArchivedAtDesc(PROJECT_ID))
                .thenReturn(List.of(song, note));

        var vm = service.getArchiveViewModel(PROJECT_ID, user);

        assertNotNull(vm);
        assertEquals(1, vm.getSongs().size());
        assertEquals(1, vm.getDrafts().size());
        assertEquals("Opening Number", vm.getSongs().get(0).getTitle());
        assertEquals(archivedAt, vm.getSongs().get(0).getArchivedAt());
        assertNull(vm.getSongs().get(0).getPurgesAt(), "nothing expires out of the archive");
        assertTrue(vm.isRetentionUnlimited());
    }

    @Test
    void archiveViewModelIsNullWithoutProjectAccess() {
        when(projectRepository.findWithTeamsById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, user)).thenReturn(false);

        assertNull(service.getArchiveViewModel(PROJECT_ID, user));
    }

    @Test
    void theListLeavesArchivedDocumentsOutAndCountsThemSeparately() {
        when(projectRepository.findWithTeamsById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, user)).thenReturn(true);
        when(textDocumentRepository
                .findByProjectIdAndDeletedAtIsNullAndArchivedAtIsNullOrderBySortOrderAscUpdatedAtDesc(PROJECT_ID))
                .thenReturn(List.of(song));
        when(textDocumentRepository
                .countByProjectIdAndDocumentTypeAndArchivedAtIsNotNullAndDeletedAtIsNull(
                        PROJECT_ID, TextDocument.TYPE_SONG))
                .thenReturn(2);
        when(textDocumentRepository.countByProjectIdAndArchivedAtIsNotNullAndDeletedAtIsNull(PROJECT_ID))
                .thenReturn(5);

        var vm = service.getListViewModel(PROJECT_ID, user);

        assertNotNull(vm);
        assertEquals(1, vm.getSongs().size());
        assertEquals(2, vm.getArchivedSongCount());
        // Drafts are counted by subtraction, as the trashed ones are.
        assertEquals(3, vm.getArchivedDraftCount());
    }
}
