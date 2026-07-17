package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TextDocumentServiceImplDeleteSongsTest {

    private TextDocumentRepository textDocumentRepository;
    private ProjectService projectService;
    private TextDocumentServiceImpl service;

    private Project project;
    private User user;
    private TextDocument song;
    private TextDocument otherSong;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectService = mock(ProjectService.class);
        service = new TextDocumentServiceImpl(
                textDocumentRepository,
                mock(ProjectRepository.class),
                mock(BlockRepository.class),
                mock(BlockService.class),
                projectService,
                mock(ScriptImportTextExtractor.class),
                mock(ProjectActivityService.class),
                mock(ScriptEditionService.class),
                mock(EmailService.class));

        project = new Project();
        project.setId(7);
        project.setTitle("The Big Musical");

        user = new User();
        user.setId(3);
        user.setUsername("writer");

        song = new TextDocument();
        song.setId(42);
        song.setProject(project);
        song.setDocumentType(TextDocument.TYPE_SONG);
        song.setTitle("Opening Number");

        otherSong = new TextDocument();
        otherSong.setId(43);
        otherSong.setProject(project);
        otherSong.setDocumentType(TextDocument.TYPE_SONG);
        otherSong.setTitle("Act Two Finale");
    }

    @Test
    void deleteSongsTrashesEverySelectedSong() {
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(42, 7)).thenReturn(Optional.of(song));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(43, 7)).thenReturn(Optional.of(otherSong));
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        assertEquals(2, service.deleteSongs(List.of(42, 43), 7, user));

        // A bulk delete is still recoverable: it trashes rather than destroys.
        assertTrue(song.isDeleted());
        assertTrue(otherSong.isDeleted());
        verify(textDocumentRepository).save(song);
        verify(textDocumentRepository).save(otherSong);
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }

    @Test
    void deleteSongsCountsRepeatedIdsOnce() {
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(42, 7)).thenReturn(Optional.of(song));
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        assertEquals(1, service.deleteSongs(List.of(42, 42), 7, user));
    }

    @Test
    void deleteSongsSkipsMissingAndNonSongIdsButTrashesTheRest() {
        otherSong.setDocumentType(TextDocument.TYPE_NOTES);
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(42, 7)).thenReturn(Optional.of(song));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(43, 7)).thenReturn(Optional.of(otherSong));
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(99, 7)).thenReturn(Optional.empty());
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        assertEquals(1, service.deleteSongs(Arrays.asList(42, 43, 99, null), 7, user));

        assertTrue(song.isDeleted());
        assertFalse(otherSong.isDeleted(), "a stale selection must not take a note with it");
    }

    @Test
    void deleteSongsRequiresProjectAccess() {
        when(textDocumentRepository.findByIdAndProjectIdAndDeletedAtIsNull(42, 7)).thenReturn(Optional.of(song));
        when(projectService.canUserAccessProject(7, user)).thenReturn(false);

        assertEquals(0, service.deleteSongs(List.of(42), 7, user));

        verify(textDocumentRepository, never()).save(any(TextDocument.class));
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }

    @Test
    void deleteSongsRejectsEmptySelection() {
        assertEquals(0, service.deleteSongs(List.of(), 7, user));
        assertEquals(0, service.deleteSongs(null, 7, user));
        assertEquals(0, service.deleteSongs(List.of(42), null, user));
        assertEquals(0, service.deleteSongs(List.of(42), 7, null));

        verify(textDocumentRepository, never()).save(any(TextDocument.class));
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }
}
