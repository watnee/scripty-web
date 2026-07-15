package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the song block editor's seeding and content-sync logic (the ordering shifts
 * themselves are exercised end-to-end against H2). Repositories are mocked; saves echo the block
 * back with a generated id so ordering/content can be asserted.
 */
class SongBlockServiceImplTest {

    private TextDocumentRepository textDocumentRepository;
    private BlockRepository blockRepository;
    private ProjectRepository projectRepository;
    private ProjectActivityService projectActivityService;
    private SongBlockServiceImpl service;

    private Project project;
    private TextDocument doc;

    @BeforeEach
    void setUp() {
        textDocumentRepository = mock(TextDocumentRepository.class);
        blockRepository = mock(BlockRepository.class);
        projectRepository = mock(ProjectRepository.class);
        projectActivityService = mock(ProjectActivityService.class);
        service = new SongBlockServiceImpl(
                textDocumentRepository, blockRepository, projectRepository, projectActivityService);

        project = new Project();
        project.setId(7);
        doc = new TextDocument();
        doc.setId(1);
        doc.setTitle("My Song");
        doc.setProject(project);
        doc.setContent("Line one\nLine two");

        when(textDocumentRepository.findById(1)).thenReturn(Optional.of(doc));
        // Saves assign a sequential id and echo the block back.
        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> {
            Block b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(100 + b.getOrder());
            }
            return b;
        });
    }

    @Test
    void ensureBlocksSeedsOneLyricsBlockPerLineWithoutTouchingContent() {
        when(blockRepository.findByTextDocumentIdOrderByOrderAsc(1)).thenReturn(new ArrayList<>());

        List<Block> created = service.ensureBlocks(1);

        assertEquals(2, created.size());
        assertEquals("Line one", created.get(0).getContent());
        assertEquals(1, created.get(0).getOrder());
        assertEquals("Line two", created.get(1).getContent());
        assertEquals(2, created.get(1).getOrder());
        assertTrue(created.stream().allMatch(b -> Block.TYPE_LYRICS.equals(b.getType())));
        assertTrue(created.stream().allMatch(b -> doc.equals(b.getTextDocument())));
        assertTrue(created.stream().allMatch(b -> project.equals(b.getProject())));
        // Seeding must not rewrite content or record activity (it is not a mutation).
        assertEquals("Line one\nLine two", doc.getContent());
        verify(projectActivityService, never()).recordForCurrentUser(any(), any(), any(), any(), any());
    }

    @Test
    void ensureBlocksReturnsExistingWithoutReseeding() {
        List<Block> existing = List.of(songBlock(50, 1, "Kept"));
        when(blockRepository.findByTextDocumentIdOrderByOrderAsc(1)).thenReturn(existing);

        List<Block> result = service.ensureBlocks(1);

        assertEquals(existing, result);
        verify(blockRepository, never()).save(any(Block.class));
    }

    @Test
    void ensureBlocksCreatesSingleEmptyBlockWhenContentBlank() {
        doc.setContent("");
        when(blockRepository.findByTextDocumentIdOrderByOrderAsc(1)).thenReturn(new ArrayList<>());

        List<Block> created = service.ensureBlocks(1);

        assertEquals(1, created.size());
        assertEquals("", created.get(0).getContent());
        assertEquals(1, created.get(0).getOrder());
    }

    @Test
    void editContentUpdatesBlockAndRecomputesDocumentContent() {
        Block b1 = songBlock(101, 1, "Line one");
        Block b2 = songBlock(102, 2, "Line two");
        when(blockRepository.findById(101)).thenReturn(Optional.of(b1));
        // After editing, afterMutation re-reads the ordered blocks to rebuild content.
        when(blockRepository.findByTextDocumentIdOrderByOrderAsc(1))
                .thenReturn(List.of(b1, b2));

        Block edited = service.editContent(101, "Line one edited");

        assertEquals("Line one edited", edited.getContent());
        // Document content is the newline-join of the blocks (source of truth for export/insert).
        assertEquals("Line one edited\nLine two", doc.getContent());
        verify(projectActivityService).recordForCurrentUser(
                eq(7), any(), any(), any(), eq(1));
    }

    @Test
    void createBelowInsertsAfterAnchorAndShiftsOrders() {
        Block anchor = songBlock(101, 1, "Line one");
        when(blockRepository.findById(101)).thenReturn(Optional.of(anchor));
        when(blockRepository.findByTextDocumentIdOrderByOrderAsc(1)).thenReturn(List.of(anchor));

        ArgumentCaptor<Block> saved = ArgumentCaptor.forClass(Block.class);

        Block created = service.createBelow(101, "Inserted");

        // Orders above the anchor are shifted up before the insert.
        verify(blockRepository).incrementOrdersAboveDoc(1, 1);
        assertEquals("Inserted", created.getContent());
        assertEquals(2, created.getOrder());
        assertEquals(Block.TYPE_LYRICS, created.getType());
    }

    private Block songBlock(int id, int order, String content) {
        Block b = new Block();
        b.setId(id);
        b.setOrder(order);
        b.setContent(content);
        b.setType(Block.TYPE_LYRICS);
        b.setProject(project);
        b.setTextDocument(doc);
        return b;
    }
}
