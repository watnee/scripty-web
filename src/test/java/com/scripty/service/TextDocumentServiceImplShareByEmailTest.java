package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
    }

    @Test
    void shareSongByEmailSendsLyricsAndRecordsActivity() {
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));
        when(projectService.canUserAccessProject(7, user)).thenReturn(true);

        TextDocument shared = service.shareSongByEmail(42, " friend@example.com ", user);

        assertNotNull(shared);
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
    void shareSongByEmailRejectsInvalidAddress() {
        assertNull(service.shareSongByEmail(42, "not-an-email", user));
        assertNull(service.shareSongByEmail(42, "   ", user));
        assertNull(service.shareSongByEmail(42, null, user));
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shareSongByEmailRejectsNonSongDocuments() {
        song.setDocumentType(TextDocument.TYPE_NOTES);
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));

        assertNull(service.shareSongByEmail(42, "friend@example.com", user));
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shareSongByEmailRequiresProjectAccess() {
        when(textDocumentRepository.findById(42)).thenReturn(Optional.of(song));
        when(projectService.canUserAccessProject(7, user)).thenReturn(false);

        assertNull(service.shareSongByEmail(42, "friend@example.com", user));
        verify(emailService, never()).send(anyString(), anyString(), anyString());
    }
}
