package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TextDocumentServiceImplSoftDeleteTest {

    @Mock
    private TextDocumentRepository textDocumentRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private BlockService blockService;
    @Mock
    private ProjectService projectService;
    @Mock
    private ScriptImportTextExtractor scriptImportTextExtractor;
    @Mock
    private ProjectActivityService projectActivityService;
    @Mock
    private ScriptEditionService scriptEditionService;

    private TextDocumentServiceImpl service;

    private User user;
    private Project project;
    private TextDocument doc;

    @BeforeEach
    void setUp() {
        service = new TextDocumentServiceImpl(
                textDocumentRepository,
                projectRepository,
                blockRepository,
                blockService,
                projectService,
                scriptImportTextExtractor,
                projectActivityService,
                scriptEditionService);

        user = new User();
        user.setId(7);

        project = new Project();
        project.setId(3);

        doc = new TextDocument();
        doc.setId(11);
        doc.setProject(project);
        doc.setTitle("My Song");
        doc.setDocumentType(TextDocument.TYPE_SONG);
    }

    @Test
    void deleteMarksDocumentDeletedInsteadOfRemovingIt() {
        when(projectService.canUserAccessProject(3, user)).thenReturn(true);
        when(textDocumentRepository.findByIdAndProjectId(11, 3)).thenReturn(Optional.of(doc));

        service.delete(11, 3, user);

        assertNotNull(doc.getDeletedAt());
        verify(textDocumentRepository).save(doc);
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
        verify(projectActivityService).record(
                eq(3), eq(7), eq(ProjectActivity.ACTION_DOCUMENT_DELETED),
                any(), eq(ProjectActivity.ENTITY_DOCUMENT), eq(11));
    }

    @Test
    void deleteIgnoresAlreadyDeletedDocument() {
        doc.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(projectService.canUserAccessProject(3, user)).thenReturn(true);
        when(textDocumentRepository.findByIdAndProjectId(11, 3)).thenReturn(Optional.of(doc));

        service.delete(11, 3, user);

        verify(textDocumentRepository, never()).save(any(TextDocument.class));
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));
    }

    @Test
    void restoreClearsDeletedAt() {
        doc.setDeletedAt(LocalDateTime.now().minusDays(2));
        when(projectService.canUserAccessProject(3, user)).thenReturn(true);
        when(textDocumentRepository.findByIdAndProjectId(11, 3)).thenReturn(Optional.of(doc));
        when(textDocumentRepository.save(doc)).thenReturn(doc);

        TextDocument restored = service.restore(11, 3, user);

        assertNotNull(restored);
        assertNull(restored.getDeletedAt());
        verify(projectActivityService).record(
                eq(3), eq(7), eq(ProjectActivity.ACTION_DOCUMENT_RESTORED),
                any(), eq(ProjectActivity.ENTITY_DOCUMENT), eq(11));
    }

    @Test
    void restoreIgnoresDocumentThatIsNotDeleted() {
        when(projectService.canUserAccessProject(3, user)).thenReturn(true);
        when(textDocumentRepository.findByIdAndProjectId(11, 3)).thenReturn(Optional.of(doc));

        assertNull(service.restore(11, 3, user));
        verify(textDocumentRepository, never()).save(any(TextDocument.class));
    }

    @Test
    void deletePermanentlyOnlyRemovesAlreadyDeletedDocuments() {
        when(projectService.canUserAccessProject(3, user)).thenReturn(true);
        when(textDocumentRepository.findByIdAndProjectId(11, 3)).thenReturn(Optional.of(doc));

        service.deletePermanently(11, 3, user);
        verify(textDocumentRepository, never()).delete(any(TextDocument.class));

        doc.setDeletedAt(LocalDateTime.now().minusDays(5));
        service.deletePermanently(11, 3, user);
        verify(textDocumentRepository).delete(doc);
    }

    @Test
    void purgeRemovesDocumentsPastRetentionWindow() {
        TextDocument expired = new TextDocument();
        expired.setId(20);
        expired.setDeletedAt(LocalDateTime.now().minusDays(31));
        when(textDocumentRepository.findByDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(expired));

        int purged = service.purgeExpiredDeleted();

        assertEquals(1, purged);
        verify(textDocumentRepository).deleteAll(List.of(expired));

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(textDocumentRepository).findByDeletedAtBefore(cutoff.capture());
        LocalDateTime expected = LocalDateTime.now().minusDays(TextDocumentService.TRASH_RETENTION_DAYS);
        assertEquals(0, java.time.Duration.between(cutoff.getValue(), expected).toMinutes());
    }

    @Test
    void deletedDocumentIsHiddenFromEditAndInsert() {
        doc.setDeletedAt(LocalDateTime.now());
        when(textDocumentRepository.findById(11)).thenReturn(Optional.of(doc));

        assertNull(service.getViewModel(11, user));
        assertNull(service.getCommandModel(11, user));
        assertEquals(List.of(), service.insertIntoScript(11, null, null, user));
    }
}
