package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
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
import org.mockito.ArgumentCaptor;

class TextDocumentServiceImplShareByEmailTest {

    private TextDocumentRepository textDocumentRepository;
    private ProjectService projectService;
    private ProjectActivityService projectActivityService;
    private EmailService emailService;
    private TextDocumentServiceImpl service;

    private Project project;
    private User user;
    private TextDocument song;
    private TextDocument otherSong;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectService = mock(ProjectService.class);
        projectActivityService = mock(ProjectActivityService.class);
        emailService = mock(EmailService.class);
        service = new TextDocumentServiceImpl(
                textDocumentRepository,
                mock(ProjectRepository.class),
                mock(BlockRepository.class),
                mock(BlockService.class),
                projectService,
                mock(ScriptImportTextExtractor.class),
                projectActivityService,
                mock(ScriptEditionService.class),
                emailService);

        project = new Project();
        project.setId(7);
        project.setTitle("The Big Musical");

        user = new User();
        user.setId(3);
        user.setUsername("writer");
        user.setFirstName("Wanda");
        user.setLastName("Writer");

        song = new TextDocument();
        song.setId(42);
        song.setProject(project);
        song.setDocumentType(TextDocument.TYPE_SONG);
        song.setTitle("Opening Number");
        song.setContent("Verse one\nChorus <>&");

        otherSong = new TextDocument();
        otherSong.setId(43);
        otherSong.setProject(project);
        otherSong.setDocumentType(TextDocument.TYPE_SONG);
        otherSong.setTitle("Act Two Finale");
        otherSong.setContent("Finale lines");
    }

    @Test
    void shareSongsByEmailSendsLyricsAndRecordsActivity() {
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        List<TextDocument> shared = service.shareSongsByEmail(List.of(42), " friend@example.com ", user);

        assertEquals(List.of(song), shared);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(
                eq("friend@example.com"),
                eq("Wanda Writer shared a song with you: Opening Number"),
                body.capture());
        assertTrue(body.getValue().contains("Verse one\nChorus &lt;&gt;&amp;"));
        verify(projectActivityService).record(
                eq(7), eq(3),
                eq(ProjectActivity.ACTION_DOCUMENT_SHARED),
                contains("friend@example.com"),
                eq(ProjectActivity.ENTITY_DOCUMENT), eq(42));
    }

    @Test
    void shareSongsByEmailSendsSelectedSongsAsOneMessage() {
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));
        when(textDocumentRepository.findById(43)).thenReturn(Optional.of(otherSong));
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        List<TextDocument> shared = service.shareSongsByEmail(List.of(42, 43), "friend@example.com", user);

        assertEquals(List.of(song, otherSong), shared);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(
                eq("friend@example.com"),
                eq("Wanda Writer shared 2 songs with you"),
                body.capture());
        assertTrue(body.getValue().contains("Opening Number"));
        assertTrue(body.getValue().contains("Verse one\nChorus &lt;&gt;&amp;"));
        assertTrue(body.getValue().contains("Act Two Finale"));
        assertTrue(body.getValue().contains("Finale lines"));
        verify(projectActivityService, times(2)).record(
                eq(7), eq(3),
                eq(ProjectActivity.ACTION_DOCUMENT_SHARED),
                contains("friend@example.com"),
                eq(ProjectActivity.ENTITY_DOCUMENT), anyInt());
    }

    @Test
    void shareSongsByEmailSkipsUnshareableIdsButSendsTheRest() {
        otherSong.setDocumentType(TextDocument.TYPE_NOTES);
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));
        when(textDocumentRepository.findById(43)).thenReturn(Optional.of(otherSong));
        when(textDocumentRepository.findById(99)).thenReturn(Optional.empty());
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        List<TextDocument> shared = service.shareSongsByEmail(Arrays.asList(42, 43, 99, null), "friend@example.com", user);

        assertEquals(List.of(song), shared);
        verify(emailService).send(
                eq("friend@example.com"),
                eq("Wanda Writer shared a song with you: Opening Number"),
                anyString());
    }

    @Test
    void shareSongsByEmailRejectsInvalidAddress() {
        assertTrue(service.shareSongsByEmail(List.of(42), "not-an-email", user).isEmpty());
        assertTrue(service.shareSongsByEmail(List.of(42), "   ", user).isEmpty());
        assertTrue(service.shareSongsByEmail(List.of(42), null, user).isEmpty());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shareSongsByEmailRejectsEmptySelection() {
        assertTrue(service.shareSongsByEmail(List.of(), "friend@example.com", user).isEmpty());
        assertTrue(service.shareSongsByEmail(null, "friend@example.com", user).isEmpty());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shareSongsByEmailRejectsNonSongDocuments() {
        song.setDocumentType(TextDocument.TYPE_NOTES);
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));

        assertTrue(service.shareSongsByEmail(List.of(42), "friend@example.com", user).isEmpty());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shareSongsByEmailRequiresProjectAccess() {
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));
        when(projectService.canUserAccessProject(7, user)).thenReturn(false);

        assertTrue(service.shareSongsByEmail(List.of(42), "friend@example.com", user).isEmpty());
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }
}
