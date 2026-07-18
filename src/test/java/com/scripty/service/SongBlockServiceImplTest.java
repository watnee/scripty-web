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
import com.scripty.dto.SongEdition;
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

    private static final Integer EDITION_ID = 100;

    private final Map<Integer, SongBlock> store = new HashMap<>();
    private int nextId = 1;

    private SongBlockRepository songBlockRepository;
    private TextDocumentRepository textDocumentRepository;
    private ProjectRepository projectRepository;
    private SongEditionService songEditionService;
    private SongBlockServiceImpl service;

    private TextDocument doc;
    private SongEdition edition;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store.clear();
        nextId = 1;
        songBlockRepository = mock(SongBlockRepository.class);
        textDocumentRepository = mock(TextDocumentRepository.class);
        projectRepository = mock(ProjectRepository.class);
        songEditionService = mock(SongEditionService.class);

        Project project = new Project();
        project.setId(42);
        doc = new TextDocument();
        doc.setId(7);
        doc.setProject(project);
        doc.setDocumentType(TextDocument.TYPE_SONG);
        doc.setContent("");

        // The active version of this song: default and published, so the impl
        // mirrors its blocks onto the document content just like the old 1-arg API.
        edition = new SongEdition();
        edition.setId(EDITION_ID);
        edition.setName("Main");
        edition.setDefault(true);
        edition.setPublished(true);
        edition.setTextDocument(doc);

        when(textDocumentRepository.findByIdAndDeletedAtIsNull(7)).thenReturn(Optional.of(doc));
        when(textDocumentRepository.save(any(TextDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        when(songEditionService.requireForDocument(anyInt(), any())).thenReturn(edition);
        when(songEditionService.ensureDefaultEdition(anyInt())).thenReturn(edition);

        // A version's live lines: scoped to the edition and excluding the
        // soft-deleted (trashed) ones, matching how newBlock stamps each block.
        when(songBlockRepository.findBySongEditionIdAndDeletedAtIsNullOrderByOrderAsc(anyInt())).thenAnswer(i -> {
            Integer editionId = i.getArgument(0);
            return store.values().stream()
                    .filter(b -> b.getSongEdition() != null && editionId.equals(b.getSongEdition().getId()))
                    .filter(b -> b.getDeletedAt() == null)
                    .sorted(Comparator.comparingInt(SongBlock::getOrder))
                    .collect(Collectors.toList());
        });
        // Recovery stays document-scoped: trashed lines from every version of
        // the song surface in the "recently deleted lines" list.
        when(songBlockRepository.findByTextDocumentIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(anyInt())).thenAnswer(i -> {
            Integer docId = i.getArgument(0);
            return store.values().stream()
                    .filter(b -> b.getTextDocument() != null && docId.equals(b.getTextDocument().getId()))
                    .filter(b -> b.getDeletedAt() != null)
                    .sorted(Comparator.comparing(SongBlock::getDeletedAt).reversed())
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

        service = new SongBlockServiceImpl(
                songBlockRepository, textDocumentRepository, projectRepository, songEditionService);
    }

    private List<String> contents() {
        return service.getBlocks(7, EDITION_ID).stream()
                .map(SongBlockViewModel::getContent)
                .collect(Collectors.toList());
    }

    private int deletedCount() {
        return service.getDeletedBlocksViewModel(7).getBlocks().size();
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
        List<SongBlockViewModel> blocks = service.getBlocks(7, EDITION_ID);
        Integer firstId = blocks.get(0).getId();

        SongBlock created = service.createBelow(firstId, "a-edited");
        assertNotNull(created);

        assertEquals(List.of("a-edited", "", "b"), contents());
        assertEquals("a-edited\n\nb", doc.getContent());
    }

    @Test
    void deleteKeepsAtLeastOneBlock() {
        doc.setContent("only line");
        Integer id = service.getBlocks(7, EDITION_ID).get(0).getId();

        service.deleteBlock(id);

        List<String> after = contents();
        assertEquals(1, after.size());
        assertEquals("", after.get(0));
        assertEquals("", doc.getContent());
    }

    @Test
    void deleteRemovesTargetAndRenumbers() {
        doc.setContent("a\nb\nc");
        List<SongBlockViewModel> blocks = service.getBlocks(7, EDITION_ID);
        Integer middle = blocks.get(1).getId();

        service.deleteBlock(middle);

        assertEquals(List.of("a", "c"), contents());
        assertEquals("a\nc", doc.getContent());
    }

    @Test
    void deleteSoftDeletesSoLineIsRecoverable() {
        doc.setContent("a\nb\nc");
        Integer middle = service.getBlocks(7, EDITION_ID).get(1).getId();

        service.deleteBlock(middle);

        // Dropped from the live song...
        assertEquals(List.of("a", "c"), contents());
        // ...but kept in the recovery list.
        var trash = service.getDeletedBlocksViewModel(7);
        assertEquals(1, trash.getBlocks().size());
        assertEquals("b", trash.getBlocks().get(0).getContent());
        assertNotNull(trash.getBlocks().get(0).getDeletedAt());
        assertNotNull(trash.getBlocks().get(0).getPurgesAt());
        assertEquals(1, deletedCount());
    }

    @Test
    void restoreBringsLineBackAtEndOfSong() {
        doc.setContent("a\nb\nc");
        Integer middle = service.getBlocks(7, EDITION_ID).get(1).getId();
        service.deleteBlock(middle);

        Integer restoredDoc = service.restoreBlock(middle);

        assertEquals(7, restoredDoc);
        assertEquals(List.of("a", "c", "b"), contents());
        assertEquals("a\nc\nb", doc.getContent());
        // The recovery list is empty again.
        assertEquals(0, deletedCount());
    }

    @Test
    void restoreOfLiveOrMissingBlockReturnsNull() {
        doc.setContent("a\nb");
        Integer live = service.getBlocks(7, EDITION_ID).get(0).getId();

        assertEquals(null, service.restoreBlock(live));
        assertEquals(null, service.restoreBlock(9999));
    }

    @Test
    void purgeRemovesTrashedLineForGood() {
        doc.setContent("a\nb");
        Integer second = service.getBlocks(7, EDITION_ID).get(1).getId();
        service.deleteBlock(second);
        assertEquals(1, deletedCount());

        Integer purgedDoc = service.purgeBlock(second);

        assertEquals(7, purgedDoc);
        assertEquals(0, deletedCount());
        assertFalse(store.containsKey(second));
    }

    @Test
    void purgeOfLiveBlockReturnsNullAndKeepsIt() {
        doc.setContent("a\nb");
        Integer live = service.getBlocks(7, EDITION_ID).get(0).getId();

        assertEquals(null, service.purgeBlock(live));
        assertEquals(List.of("a", "b"), contents());
    }

    @Test
    void moveDownReordersBlocks() {
        doc.setContent("a\nb\nc");
        List<SongBlockViewModel> blocks = service.getBlocks(7, EDITION_ID);
        Integer first = blocks.get(0).getId();

        service.moveDown(first);

        assertEquals(List.of("b", "a", "c"), contents());
    }

    @Test
    void moveUpAtTopIsNoOp() {
        doc.setContent("a\nb");
        Integer first = service.getBlocks(7, EDITION_ID).get(0).getId();

        service.moveUp(first);

        assertEquals(List.of("a", "b"), contents());
    }

    @Test
    void moveToDropsBlockAtRequestedIndex() {
        doc.setContent("a\nb\nc\nd");
        Integer first = service.getBlocks(7, EDITION_ID).get(0).getId();

        service.moveTo(first, 2);

        assertEquals(List.of("b", "c", "a", "d"), contents());
        assertEquals("b\nc\na\nd", doc.getContent());
    }

    @Test
    void moveToDragsBlockUpwards() {
        doc.setContent("a\nb\nc");
        Integer last = service.getBlocks(7, EDITION_ID).get(2).getId();

        service.moveTo(last, 0);

        assertEquals(List.of("c", "a", "b"), contents());
    }

    @Test
    void moveToClampsOutOfRangePosition() {
        doc.setContent("a\nb\nc");
        Integer first = service.getBlocks(7, EDITION_ID).get(0).getId();

        service.moveTo(first, 99);

        assertEquals(List.of("b", "c", "a"), contents());
    }

    @Test
    void moveToSamePositionIsNoOp() {
        doc.setContent("a\nb\nc");
        Integer middle = service.getBlocks(7, EDITION_ID).get(1).getId();

        service.moveTo(middle, 1);

        assertEquals(List.of("a", "b", "c"), contents());
    }

    @Test
    void editContentUpdatesDocumentJoin() {
        doc.setContent("a\nb");
        Integer second = service.getBlocks(7, EDITION_ID).get(1).getId();

        service.editContent(second, "b-new");

        assertEquals(List.of("a", "b-new"), contents());
        assertEquals("a\nb-new", doc.getContent());
    }

    @Test
    void appendAddsEmptyBlockAtEnd() {
        doc.setContent("a");
        service.appendBlock(7, EDITION_ID);

        assertEquals(List.of("a", ""), contents());
    }

    @Test
    void projectAndDocumentLookupsResolve() {
        Integer id = service.getBlocks(7, EDITION_ID).get(0).getId();
        assertEquals(42, service.projectIdForBlock(id));
        assertEquals(42, service.projectIdForDocument(7));
        assertEquals(7, service.documentIdForBlock(id));
        assertFalse(service.projectIdForDocument(7) == null);
    }
}
