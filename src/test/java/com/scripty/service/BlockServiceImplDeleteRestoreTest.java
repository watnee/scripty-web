package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Block;
import com.scripty.dto.DeletedBlock;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.DeletedBlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The delete → capture and restore-from-record halves of the block trash. */
class BlockServiceImplDeleteRestoreTest {

    private static final int PROJECT_ID = 5;
    private static final int EDITION_ID = 9;

    private BlockRepository blockRepository;
    private DeletedBlockRepository deletedBlockRepository;
    private ScriptEditionService scriptEditionService;
    private BlockServiceImpl service;

    private Project project;
    private ScriptEdition edition;

    @BeforeEach
    void setUp() {
        blockRepository = mock(BlockRepository.class);
        deletedBlockRepository = mock(DeletedBlockRepository.class);
        scriptEditionService = mock(ScriptEditionService.class);
        service = new BlockServiceImpl(
                blockRepository,
                deletedBlockRepository,
                mock(PersonRepository.class),
                mock(ProjectRepository.class),
                mock(ProjectActivityService.class),
                mock(ProjectUndoRedoService.class),
                scriptEditionService,
                mock(UserService.class));

        project = new Project();
        project.setId(PROJECT_ID);

        edition = new ScriptEdition();
        edition.setId(EDITION_ID);
        edition.setProject(project);
    }

    private Block block(int id, int order, String content) {
        Block block = new Block();
        block.setId(id);
        block.setOrder(order);
        block.setContent(content);
        block.setType(Block.TYPE_ACTION);
        block.setProject(project);
        block.setScriptEdition(edition);
        return block;
    }

    @Test
    void deleteCapturesBlockContentIntoTheTrashBeforeRemoving() {
        Block block = block(2, 3, "Do not lose me");
        block.setHighlight(Block.HIGHLIGHT_YELLOW);
        when(blockRepository.findById(2)).thenReturn(Optional.of(block));

        service.deleteBlock(2);

        ArgumentCaptor<DeletedBlock> captor = ArgumentCaptor.forClass(DeletedBlock.class);
        verify(deletedBlockRepository).save(captor.capture());
        DeletedBlock captured = captor.getValue();
        assertEquals("Do not lose me", captured.getContent());
        assertEquals(3, captured.getOriginalOrder());
        assertEquals(Block.HIGHLIGHT_YELLOW, captured.getHighlight());
        assertEquals(PROJECT_ID, captured.getProject().getId());
        // The row itself is still hard-deleted; the trash is a separate copy.
        verify(blockRepository).delete(block);
    }

    @Test
    void restoreReinsertsAtStoredPositionAndOpensAGap() {
        DeletedBlock record = deletedRecord(2, "Back from the dead");
        when(blockRepository.countByScriptEditionId(EDITION_ID)).thenReturn(4);

        service.restoreBlock(record);

        // Position 2 is within [1, 5], so everything at/after 2 shifts up to make room.
        verify(blockRepository).incrementOrdersAbove(1, EDITION_ID);
        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).save(captor.capture());
        Block restored = captor.getValue();
        assertEquals(2, restored.getOrder());
        assertEquals("Back from the dead", restored.getContent());
    }

    @Test
    void restoreClampsAStalePositionToTheEndOfTheScript() {
        // The block was at order 99, but the edition now has only 4 live blocks.
        DeletedBlock record = deletedRecord(99, "Appended at the end");
        when(blockRepository.countByScriptEditionId(EDITION_ID)).thenReturn(4);

        service.restoreBlock(record);

        verify(blockRepository).incrementOrdersAbove(4, EDITION_ID);
        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getOrder());
    }

    @Test
    void restoreFallsBackToDefaultEditionWhenNoneStored() {
        DeletedBlock record = deletedRecord(1, "No edition on record");
        record.setScriptEdition(null);
        when(scriptEditionService.ensureDefaultEdition(PROJECT_ID)).thenReturn(edition);
        when(blockRepository.countByScriptEditionId(EDITION_ID)).thenReturn(0);

        service.restoreBlock(record);

        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).save(captor.capture());
        assertEquals(EDITION_ID, captor.getValue().getScriptEdition().getId());
    }

    @Test
    void restoreReturnsNullWhenNoEditionCanBeResolved() {
        DeletedBlock record = deletedRecord(1, "Orphaned");
        record.setScriptEdition(null);
        when(scriptEditionService.ensureDefaultEdition(PROJECT_ID)).thenReturn(null);

        assertNull(service.restoreBlock(record));
        verify(blockRepository, org.mockito.Mockito.never()).save(any());
    }

    private DeletedBlock deletedRecord(int originalOrder, String content) {
        DeletedBlock record = new DeletedBlock();
        record.setProject(project);
        record.setScriptEdition(edition);
        record.setOriginalOrder(originalOrder);
        record.setContent(content);
        record.setType(Block.TYPE_ACTION);
        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));
        return record;
    }
}
