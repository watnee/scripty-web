package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockServiceImplTrashTest {

    @Mock
    private BlockRepository blockRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectActivityService projectActivityService;
    @Mock
    private ProjectUndoRedoService projectUndoRedoService;
    @Mock
    private ScriptEditionService scriptEditionService;

    private BlockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BlockServiceImpl(
                blockRepository,
                personRepository,
                projectRepository,
                projectActivityService,
                projectUndoRedoService,
                scriptEditionService);
    }

    private Block trashedBlock(int id, int order, ScriptEdition edition, Project project) {
        Block block = new Block();
        block.setId(id);
        block.setOrder(order);
        block.setContent("some line");
        block.setProject(project);
        block.setScriptEdition(edition);
        block.setDeletedAt(Instant.now());
        return block;
    }

    @Test
    void restoreBlockReturnsNullWhenBlockIsNotInTrash() {
        when(blockRepository.findDeletedById(99)).thenReturn(Optional.empty());

        assertNull(service.restoreBlock(99));
        verify(blockRepository, never()).restoreDeletedById(anyInt(), anyInt());
    }

    @Test
    void restoreBlockReinsertsAtOriginalOrderWhenStillInRange() {
        Project project = new Project();
        project.setId(9);
        ScriptEdition edition = new ScriptEdition();
        edition.setId(7);
        Block trashed = trashedBlock(5, 2, edition, project);

        when(blockRepository.findDeletedById(5)).thenReturn(Optional.of(trashed));
        when(blockRepository.countByScriptEditionId(7)).thenReturn(4);
        when(blockRepository.findById(5)).thenReturn(Optional.of(trashed));

        service.restoreBlock(5);

        verify(blockRepository).incrementOrdersAbove(1, 7);
        verify(blockRepository).restoreDeletedById(5, 2);
    }

    @Test
    void restoreBlockClampsOrderWhenScriptShrankSinceDeletion() {
        Project project = new Project();
        project.setId(9);
        ScriptEdition edition = new ScriptEdition();
        edition.setId(7);
        Block trashed = trashedBlock(5, 40, edition, project);

        when(blockRepository.findDeletedById(5)).thenReturn(Optional.of(trashed));
        when(blockRepository.countByScriptEditionId(7)).thenReturn(3);
        when(blockRepository.findById(5)).thenReturn(Optional.of(trashed));

        service.restoreBlock(5);

        verify(blockRepository).incrementOrdersAbove(3, 7);
        verify(blockRepository).restoreDeletedById(5, 4);
    }

    @Test
    void purgeUsesThirtyDayCutoff() {
        when(blockRepository.purgeDeletedBefore(any())).thenReturn(2);

        int purged = service.purgeExpiredDeletedBlocks();

        assertEquals(2, purged);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(blockRepository).purgeDeletedBefore(cutoff.capture());
        Instant expected = Instant.now().minus(BlockService.TRASH_RETENTION_DAYS, ChronoUnit.DAYS);
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(expected, cutoff.getValue())) < 60);
    }
}
