package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.SongBlock;
import com.scripty.dto.TextDocument;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.SongBlockRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.viewmodel.songblock.SongBlockViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SongBlockServiceImplTest {

    private final Map<Integer, SongBlock> store = new HashMap<>();
    private int nextId = 1;

    private SongBlockRepository songBlockRepository;
    private TextDocumentRepository textDocumentRepository;
    private ProjectRepository projectRepository;
    private SongBlockServiceImpl service;

    private TextDocument doc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store.clear();
        nextId = 1;
        songBlockRepository = mock(SongBlockRepository.class);
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectRepository = mock(ProjectRepository.class);

        Project project = new Project();
        project.setId(42);
        doc = new TextDocument();
        doc.setId(7);
        doc.setProject(project);
        doc.setDocumentType(TextDocument.TYPE_SONG);
        doc.setContent("");

        when(textDocumentRepository.findById(7)).thenReturn(Optional.of(doc));
        when(textDocumentRepository.save(any(TextDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        when(songBlockRepository.findByTextDocumentIdOrderByOrderAsc(anyInt())).thenAnswer(i -> {
            Integer docId = i.getArgument(0);
            return store.values().stream()
                    .filter(b -> b.getTextDocument() != null && docId.equals(b.getTextDocument().getId()))
                    .sorted(Comparator.comparingInt(SongBlock::getOrder))
                    .collect(Collectors.toList());
        });
        when(songBlockRepository.findById(anyInt()))
                .thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
        when(songBlockRepository.save(any(SongBlock.class))).thenAnswer(i -> {
            SongBlock b = i.getArgument(0);
            if (b.getId() == null) {
                b.setId(nextId++);
            }
            store.put(b.getId(), b);
            return b;
        });
        when(songBlockRepository.saveAll(any())).thenAnswer(i -> {
            List<SongBlock> saved = new ArrayList<>();
            for (SongBlock b : (Iterable<SongBlock>) i.getArgument(0)) {
                if (b.getId() == null) {
                    b.setId(nextId++);
                }
                store.put(b.getId(), b);
                saved.add(b);
            }
            return saved;
        });
        org.mockito.Mockito.doAnswer(i -> {
            SongBlock b = i.getArgument(0);
            if (b.getId() != null) {
                store.remove(b.getId());
            }
            return null;
        }).when(songBlockRepository).delete(any(SongBlock.class));

        service = new SongBlockServiceImpl(songBlockRepository, textDocumentRepository, projectRepository);
    }

    private List<String> contents() {
        return service.getBlocks(7).stream()
                .map(SongBlockViewModel::getContent)
                .collect(Collectors.toList());
    }

    @Test
    void seedsBlocksFromContentTrimmingTrailingBlanks() {
        doc.setContent("verse one\nverse two\n\nchorus\n\n");

        assertEquals(List.of("verse one", "verse two", "", "chorus"), contents());
        // Content stays in sync with the block join.
        assertEquals("verse one\nverse two\n\nchorus", doc.getContent());
    }

    @Test
    void emptySongSeedsSingleEmptyBlock() {
        assertEquals(List.of(""), contents());
    }

    @Test
    void createBelowInsertsAfterOriginAndSavesOriginContent() {
        doc.setContent("a\nb");
        List<SongBlockViewModel> blocks = service.getBlocks(7);
        Integer firstId = blocks.get(0).getId();

        SongBlock created = service.createBelow(firstId, "a-edited");
        assertNotNull(created);

        assertEquals(List.of("a-edited", "", "b"), contents());
        assertEquals("a-edited\n\nb", doc.getContent());
    }

    @Test
    void deleteKeepsAtLeastOneBlock() {
        doc.setContent("only line");
        Integer id = service.getBlocks(7).get(0).getId();

        service.deleteBlock(id);

        List<String> after = contents();
        assertEquals(1, after.size());
        assertEquals("", after.get(0));
        assertEquals("", doc.getContent());
    }

    @Test
    void deleteRemovesTargetAndRenumbers() {
        doc.setContent("a\nb\nc");
        List<SongBlockViewModel> blocks = service.getBlocks(7);
        Integer middle = blocks.get(1).getId();

        service.deleteBlock(middle);

        assertEquals(List.of("a", "c"), contents());
        assertEquals("a\nc", doc.getContent());
    }

    @Test
    void moveDownReordersBlocks() {
        doc.setContent("a\nb\nc");
        List<SongBlockViewModel> blocks = service.getBlocks(7);
        Integer first = blocks.get(0).getId();

        service.moveDown(first);

        assertEquals(List.of("b", "a", "c"), contents());
    }

    @Test
    void moveUpAtTopIsNoOp() {
        doc.setContent("a\nb");
        Integer first = service.getBlocks(7).get(0).getId();

        service.moveUp(first);

        assertEquals(List.of("a", "b"), contents());
    }

    @Test
    void editContentUpdatesDocumentJoin() {
        doc.setContent("a\nb");
        Integer second = service.getBlocks(7).get(1).getId();

        service.editContent(second, "b-new");

        assertEquals(List.of("a", "b-new"), contents());
        assertEquals("a\nb-new", doc.getContent());
    }

    @Test
    void appendAddsEmptyBlockAtEnd() {
        doc.setContent("a");
        service.appendBlock(7);

        assertEquals(List.of("a", ""), contents());
    }

    @Test
    void projectAndDocumentLookupsResolve() {
        Integer id = service.getBlocks(7).get(0).getId();
        assertEquals(42, service.projectIdForBlock(id));
        assertEquals(42, service.projectIdForDocument(7));
        assertEquals(7, service.documentIdForBlock(id));
        assertFalse(service.projectIdForDocument(7) == null);
    }
}
