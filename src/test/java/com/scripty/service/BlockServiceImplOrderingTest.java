package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.commandmodel.block.createblock.CreateBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.DeletedBlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Block order stays unique and dense even when the stored data is not. */
class BlockServiceImplOrderingTest {

    private static final int PROJECT_ID = 5;
    private static final int EDITION_ID = 9;

    private BlockRepository blockRepository;
    private ProjectRepository projectRepository;
    private ScriptEditionService scriptEditionService;
    private BlockServiceImpl service;

    private Project project;
    private ScriptEdition edition;

    @BeforeEach
    void setUp() {
        blockRepository = mock(BlockRepository.class);
        projectRepository = mock(ProjectRepository.class);
        scriptEditionService = mock(ScriptEditionService.class);
        service = new BlockServiceImpl(
                blockRepository,
                mock(DeletedBlockRepository.class),
                mock(PersonRepository.class),
                projectRepository,
                mock(ProjectActivityService.class),
                mock(ProjectUndoRedoService.class),
                scriptEditionService,
                mock(UserService.class));

        project = new Project();
        project.setId(PROJECT_ID);
        edition = new ScriptEdition();
        edition.setId(EDITION_ID);
        edition.setProject(project);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scriptEditionService.getDefaultForProject(PROJECT_ID)).thenReturn(edition);
        when(scriptEditionService.requireForProject(eq(PROJECT_ID), any())).thenReturn(edition);
        when(blockRepository.save(any(Block.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Block block(int id, int order) {
        Block b = new Block();
        b.setId(id);
        b.setOrder(order);
        b.setType(Block.TYPE_ACTION);
        b.setContent("b" + id);
        b.setProject(project);
        b.setScriptEdition(edition);
        return b;
    }

    @Test
    void newBlockAppendsAfterHighestOrderNotRowCount() {
        // Orders are 1 and 7 — a count-derived order would be 3, colliding on
        // any later renumber and, historically, duplicating an existing order.
        when(blockRepository.findMaxOrderByScriptEditionId(EDITION_ID)).thenReturn(7);
        when(blockRepository.countByScriptEditionId(EDITION_ID)).thenReturn(2);

        CreateBlockCommandModel cmd = new CreateBlockCommandModel();
        cmd.setProjectId(PROJECT_ID);
        cmd.setType(Block.TYPE_ACTION);
        cmd.setContent("appended");

        Block saved = service.saveCreateBlockCommandModel(cmd);

        assertEquals(8, saved.getOrder(), "should append after the highest order");
    }

    @Test
    void firstBlockInAnEmptyEditionGetsOrderOne() {
        when(blockRepository.findMaxOrderByScriptEditionId(EDITION_ID)).thenReturn(null);

        CreateBlockCommandModel cmd = new CreateBlockCommandModel();
        cmd.setProjectId(PROJECT_ID);
        cmd.setType(Block.TYPE_ACTION);
        cmd.setContent("first");

        assertEquals(1, service.saveCreateBlockCommandModel(cmd).getOrder());
    }

    @Test
    void moveUpSwapsNeighboursAndRepairsDuplicateOrders() {
        // Real corrupted shape: three blocks all stored at order 1.
        Block first = block(101, 1);
        Block second = block(102, 1);
        Block third = block(103, 1);
        when(blockRepository.findById(102)).thenReturn(Optional.of(second));
        when(blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(EDITION_ID))
                .thenReturn(new ArrayList<>(List.of(first, second, third)));

        service.moveBlockUp(102);

        List<Block> savedOrder = captureSavedOrder();
        assertIterableEquals(List.of(102, 101, 103),
                savedOrder.stream().map(Block::getId).toList(),
                "moved block should now precede its former neighbour");
        assertIterableEquals(List.of(1, 2, 3),
                savedOrder.stream().map(Block::getOrder).toList(),
                "duplicate orders should be renumbered dense");
    }

    @Test
    void moveDownAtTheEndOfTheScriptIsANoOp() {
        Block first = block(101, 1);
        Block last = block(102, 2);
        when(blockRepository.findById(102)).thenReturn(Optional.of(last));
        when(blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(EDITION_ID))
                .thenReturn(new ArrayList<>(List.of(first, last)));

        service.moveBlockDown(102);

        assertEquals(1, first.getOrder());
        assertEquals(2, last.getOrder());
    }

    @SuppressWarnings("unchecked")
    private List<Block> captureSavedOrder() {
        ArgumentCaptor<List<Block>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(blockRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
