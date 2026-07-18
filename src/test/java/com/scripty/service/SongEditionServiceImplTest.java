package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.SongBlock;
import com.scripty.dto.SongEdition;
import com.scripty.dto.TextDocument;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.SongEditionRepository;
import com.scripty.repository.TextDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pure-Mockito coverage for {@link SongEditionServiceImpl}, mirroring the
 * ScriptEdition service tests. The song is a {@link TextDocument}; a version
 * owns its own {@link SongBlock} lyrics and only the published version's lyrics
 * mirror onto {@link TextDocument#getContent()}.
 */
@ExtendWith(MockitoExtension.class)
class SongEditionServiceImplTest {

    private static final Integer DOC_ID = 42;
    private static final Integer PROJECT_ID = 7;

    @Mock
    private SongEditionRepository songEditionRepository;
    @Mock
    private TextDocumentRepository textDocumentRepository;
    @Mock
    private SongBlockRepository songBlockRepository;
    @Mock
    private ProjectActivityService projectActivityService;

    private SongEditionServiceImpl service;
    private TextDocument doc;

    @BeforeEach
    void setUp() {
        service = new SongEditionServiceImpl(
                songEditionRepository, textDocumentRepository, songBlockRepository, projectActivityService);

        Project project = new Project();
        project.setId(PROJECT_ID);
        doc = new TextDocument();
        doc.setId(DOC_ID);
        doc.setTitle("Song");
        doc.setProject(project);
    }

    // --- ensureDefaultEdition ---------------------------------------------

    @Test
    void ensureDefaultEditionCreatesPublishedMainWhenNoneExists() {
        when(songEditionRepository.findDefaultByTextDocumentId(DOC_ID)).thenReturn(Optional.empty());
        when(songEditionRepository.findByTextDocumentIdOrderByNameAsc(DOC_ID)).thenReturn(List.of());
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));

        SongEdition created = service.ensureDefaultEdition(DOC_ID);

        assertNotNull(created);
        assertEquals(SongEditionServiceImpl.DEFAULT_EDITION_NAME, created.getName());
        assertTrue(created.isDefault());
        assertTrue(created.isPublished());
    }

    @Test
    void ensureDefaultEditionIsIdempotentWhenDefaultAlreadyPublished() {
        SongEdition existing = edition(1, "Main", true, true);
        when(songEditionRepository.findDefaultByTextDocumentId(DOC_ID)).thenReturn(Optional.of(existing));
        when(songEditionRepository.findPublishedByTextDocumentId(DOC_ID)).thenReturn(Optional.of(existing));

        SongEdition result = service.ensureDefaultEdition(DOC_ID);

        assertSame(existing, result);
        verify(songEditionRepository, never()).save(any());
    }

    @Test
    void ensureDefaultEditionBackfillsPublishedWhenNothingPublished() {
        SongEdition existing = edition(1, "Main", true, false);
        when(songEditionRepository.findDefaultByTextDocumentId(DOC_ID)).thenReturn(Optional.of(existing));
        when(songEditionRepository.findPublishedByTextDocumentId(DOC_ID)).thenReturn(Optional.empty());
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));

        service.ensureDefaultEdition(DOC_ID);

        assertTrue(existing.isPublished());
        verify(songEditionRepository).save(existing);
    }

    // --- createEdition ----------------------------------------------------

    @Test
    void createEditionClonesSourceBlocksOntoTheNewVersionWithoutTouchingContent() {
        SongEdition source = edition(1, "Main", true, true);
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
        when(songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(DOC_ID, "Blue")).thenReturn(false);
        when(songEditionRepository.findByIdAndTextDocumentId(1, DOC_ID)).thenReturn(Optional.of(source));
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));
        when(songBlockRepository.findBySongEditionIdOrderByOrderAsc(1))
                .thenReturn(List.of(block("line one"), block("line two")));
        when(songBlockRepository.save(any(SongBlock.class))).thenAnswer(i -> i.getArgument(0));

        SongEdition created = service.createEdition(DOC_ID, "Blue", 1);

        assertNotNull(created);
        assertEquals("Blue", created.getName());
        assertFalse(created.isDefault());
        assertFalse(created.isPublished());
        assertSame(source, created.getClonedFrom());

        // A block is saved for each source line, carrying the new version.
        ArgumentCaptor<SongBlock> blocks = ArgumentCaptor.forClass(SongBlock.class);
        verify(songBlockRepository, times(2)).save(blocks.capture());
        for (SongBlock copy : blocks.getAllValues()) {
            assertSame(created, copy.getSongEdition());
        }
        // Cloning must not rewrite the shared document text.
        verify(textDocumentRepository, never()).save(any());
    }

    @Test
    void createEditionMakesTheNameUnique() {
        SongEdition source = edition(1, "Main", true, true);
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
        when(songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(DOC_ID, "Blue")).thenReturn(true);
        when(songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(DOC_ID, "Blue (2)")).thenReturn(false);
        when(songEditionRepository.findByIdAndTextDocumentId(1, DOC_ID)).thenReturn(Optional.of(source));
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));

        SongEdition created = service.createEdition(DOC_ID, "Blue", 1);

        assertEquals("Blue (2)", created.getName());
    }

    // --- renameEdition ----------------------------------------------------

    @Test
    void renameEditionRejectsBlankAndDuplicateNames() {
        SongEdition edition = edition(2, "Draft", false, false);
        when(songEditionRepository.findByIdAndTextDocumentId(2, DOC_ID)).thenReturn(Optional.of(edition));
        when(songEditionRepository.existsByTextDocumentIdAndNameIgnoreCase(DOC_ID, "Main")).thenReturn(true);

        assertFalse(service.renameEdition(2, DOC_ID, "   "));
        assertFalse(service.renameEdition(2, DOC_ID, "Main"));

        verify(songEditionRepository, never()).save(any());
    }

    // --- deleteEdition ----------------------------------------------------

    @Test
    void deleteEditionRefusesToRemoveTheLastVersion() {
        SongEdition only = edition(1, "Main", true, true);
        when(songEditionRepository.findByIdAndTextDocumentId(1, DOC_ID)).thenReturn(Optional.of(only));
        when(songEditionRepository.countByTextDocumentId(DOC_ID)).thenReturn(1L);

        assertFalse(service.deleteEdition(1, DOC_ID));

        verify(songEditionRepository, never()).delete(any());
    }

    @Test
    void deleteEditionPromotesDefaultAndPublishedToTheNextVersion() {
        SongEdition victim = edition(2, "Blue", true, true);
        SongEdition next = edition(1, "Main", false, false);
        when(songEditionRepository.findByIdAndTextDocumentId(2, DOC_ID)).thenReturn(Optional.of(victim));
        when(songEditionRepository.countByTextDocumentId(DOC_ID)).thenReturn(2L);
        when(songEditionRepository.findByTextDocumentIdOrderByNameAsc(DOC_ID)).thenReturn(List.of(next));
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));
        // The published-content resync that follows a published deletion.
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
        when(songEditionRepository.findPublishedByTextDocumentId(DOC_ID)).thenReturn(Optional.of(next));

        assertTrue(service.deleteEdition(2, DOC_ID));

        verify(songEditionRepository).delete(victim);
        ArgumentCaptor<SongEdition> promoted = ArgumentCaptor.forClass(SongEdition.class);
        verify(songEditionRepository).save(promoted.capture());
        assertSame(next, promoted.getValue());
        assertTrue(next.isDefault());
        assertTrue(next.isPublished());
    }

    // --- setDefault / setPublished ----------------------------------------

    @Test
    void setDefaultEditionLeavesExactlyOneDefault() {
        SongEdition main = edition(1, "Main", true, false);
        SongEdition blue = edition(2, "Blue", false, false);
        when(songEditionRepository.findByIdAndTextDocumentId(2, DOC_ID)).thenReturn(Optional.of(blue));
        when(songEditionRepository.findByTextDocumentIdOrderByNameAsc(DOC_ID)).thenReturn(List.of(main, blue));
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));

        assertTrue(service.setDefaultEdition(2, DOC_ID));

        assertFalse(main.isDefault());
        assertTrue(blue.isDefault());
    }

    @Test
    void setPublishedEditionClearsOthersAndResyncsDocumentContent() {
        SongEdition main = edition(1, "Main", false, true);
        SongEdition blue = edition(2, "Blue", false, false);
        when(songEditionRepository.findByIdAndTextDocumentId(2, DOC_ID)).thenReturn(Optional.of(blue));
        when(songEditionRepository.findByTextDocumentIdOrderByNameAsc(DOC_ID)).thenReturn(List.of(main, blue));
        when(songEditionRepository.save(any(SongEdition.class))).thenAnswer(i -> i.getArgument(0));
        // Resync: the shared document text is rewritten from the newly published blocks.
        when(textDocumentRepository.findByIdAndDeletedAtIsNull(DOC_ID)).thenReturn(Optional.of(doc));
        when(songEditionRepository.findPublishedByTextDocumentId(DOC_ID)).thenReturn(Optional.of(blue));
        when(songBlockRepository.findBySongEditionIdOrderByOrderAsc(2))
                .thenReturn(List.of(block("line A"), block("line B")));

        assertTrue(service.setPublishedEdition(2, DOC_ID));

        assertFalse(main.isPublished());
        assertTrue(blue.isPublished());

        ArgumentCaptor<TextDocument> savedDoc = ArgumentCaptor.forClass(TextDocument.class);
        verify(textDocumentRepository).save(savedDoc.capture());
        assertEquals("line A\nline B", savedDoc.getValue().getContent());
        verify(projectActivityService).recordForCurrentUser(eq(PROJECT_ID), any(), any(), any(), any());
    }

    // --- helpers ----------------------------------------------------------

    private SongEdition edition(int id, String name, boolean isDefault, boolean isPublished) {
        SongEdition e = new SongEdition();
        e.setId(id);
        e.setName(name);
        e.setDefault(isDefault);
        e.setPublished(isPublished);
        e.setTextDocument(doc);
        return e;
    }

    private static SongBlock block(String content) {
        SongBlock b = new SongBlock();
        b.setContent(content);
        return b;
    }
}
