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
import com.scripty.repository.TextDocumentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the delete → trash → restore/purge lifecycle for songs and notes. */
class TextDocumentServiceImplTrashTest {

    private static final int PROJECT_ID = 7;
    private static final int SONG_ID = 42;

    private TextDocumentRepository textDocumentRepository;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ProjectActivityService projectActivityService;
    private TextDocumentServiceImpl service;

    private Project project;
    private User user;
    private TextDocument song;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        projectActivityService = mock(ProjectActivityService.class);
        service = new TextDocumentServiceImpl(
                textDocumentRepository,
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

        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(true);
        when(textDocumentRepository.save(any(TextDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void deleteMarksTheDocumentTrashedInsteadOfRemovingTheRow() {
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));

        TextDocument deleted = service.delete(SONG_ID, PROJECT_ID, user);

        assertNotNull(deleted);
        assertTrue(song.isDeleted(), "delete should stamp deleted_at");
        verify(textDocumentRepository).save(song);
        // The row must survive, or the lyric blocks and version history cascade away.
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
        verify(projectActivityService).record(
                eq(PROJECT_ID),
                eq(user.getId()),
                eq(ProjectActivity.ACTION_DOCUMENT_DELETED),
                contains("trash"),
                eq(ProjectActivity.ENTITY_DOCUMENT),
                eq(SONG_ID));
    }

    @Test
    void deleteIsIgnoredWithoutProjectAccess() {
        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(false);

        assertNull(service.delete(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }

    @Test
    void restoreClearsTheTrashStampAndSendsTheSongToTheEndOfTheList() {
        song.setDeletedAt(LocalDateTime.now().minusDays(2));
        song.setSortOrder(99);
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNotNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));
        when(textDocumentRepository.countByProjectIdAndDeletedAtIsNull(PROJECT_ID)).thenReturn(4);

        TextDocument restored = service.restore(SONG_ID, PROJECT_ID, user);

        assertNotNull(restored);
        assertFalse(restored.isDeleted());
        assertEquals(4, restored.getSortOrder(), "restored document should land at the end of the list");
        verify(projectActivityService).record(
                eq(PROJECT_ID),
                eq(user.getId()),
                eq(ProjectActivity.ACTION_DOCUMENT_RESTORED),
                contains("restored"),
                eq(ProjectActivity.ENTITY_DOCUMENT),
                eq(SONG_ID));
    }

    @Test
    void restoreRejectsADocumentThatIsNotInTheTrash() {
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNotNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertNull(service.restore(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
    }

    @Test
    void purgeOnlyRemovesSomethingAlreadyInTheTrash() {
        // A live document must not be reachable by the purge endpoint, so that every
        // delete leaves a recovery window.
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNotNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertFalse(service.purge(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }

    @Test
    void purgeRemovesATrashedDocumentForGood() {
        song.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNotNull(SONG_ID, PROJECT_ID))
                .thenReturn(Optional.of(song));

        assertTrue(service.purge(SONG_ID, PROJECT_ID, user));

        verify(textDocumentRepository).delete(song);
        verify(projectActivityService).record(
                eq(PROJECT_ID),
                eq(user.getId()),
                eq(ProjectActivity.ACTION_DOCUMENT_PURGED),
                contains("permanently"),
                eq(ProjectActivity.ENTITY_DOCUMENT),
                eq(SONG_ID));
    }

    @Test
    void purgeIsIgnoredWithoutProjectAccess() {
        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(false);

        assertFalse(service.purge(SONG_ID, PROJECT_ID, user));
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }

    @Test
    void purgeExpiredRemovesOnlyDocumentsPastTheRetentionWindow() {
        when(textDocumentRepository.findByDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(song));

        assertEquals(1, service.purgeExpired());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(textDocumentRepository).findByDeletedAtBefore(cutoff.capture());
        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        assertTrue(cutoff.getValue().isAfter(expected.minusMinutes(1))
                        && cutoff.getValue().isBefore(expected.plusMinutes(1)),
                "cutoff should be the retention window before now, was " + cutoff.getValue());
        verify(textDocumentRepository).delete(song);
    }

    @Test
    void trashViewModelSplitsByTypeAndReportsThePurgeDate() {
        LocalDateTime deletedAt = LocalDateTime.now().minusDays(2);
        song.setDeletedAt(deletedAt);
        TextDocument note = new TextDocument();
        note.setId(43);
        note.setProject(project);
        note.setDocumentType(TextDocument.TYPE_NOTES);
        note.setTitle("Scene ideas");
        note.setDeletedAt(deletedAt);

        when(projectRepository.findWithTeamsById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, user)).thenReturn(true);
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(PROJECT_ID))
                .thenReturn(List.of(song, note));

        var vm = service.getTrashViewModel(PROJECT_ID, user);

        assertNotNull(vm);
        assertEquals(1, vm.getSongs().size());
        assertEquals(1, vm.getDrafts().size());
        assertEquals("Opening Number", vm.getSongs().get(0).getTitle());
        assertEquals(deletedAt, vm.getSongs().get(0).getDeletedAt());
        assertEquals(deletedAt.plusDays(30), vm.getSongs().get(0).getPurgesAt());
    }

    @Test
    void trashViewModelIsNullWithoutProjectAccess() {
        when(projectRepository.findWithTeamsById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(projectService.canUserAccessProject(project, user)).thenReturn(false);

        assertNull(service.getTrashViewModel(PROJECT_ID, user));
    }
}
