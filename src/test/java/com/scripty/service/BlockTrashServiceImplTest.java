package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Block;
import com.scripty.dto.DeletedBlock;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.User;
import com.scripty.repository.DeletedBlockRepository;
import com.scripty.repository.ScriptEditionRepository;
import com.scripty.viewmodel.block.trash.DeletedBlockListViewModel;
import com.scripty.viewmodel.block.trash.DeletedBlockViewModel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Covers the view / restore / purge side of the block trash. */
class BlockTrashServiceImplTest {

    private static final int PROJECT_ID = 7;
    private static final int DELETED_BLOCK_ID = 55;

    private DeletedBlockRepository deletedBlockRepository;
    private ScriptEditionRepository scriptEditionRepository;
    private BlockService blockService;
    private ProjectService projectService;
    private BlockTrashServiceImpl service;

    private Project project;
    private User user;
    private DeletedBlock record;

    @BeforeEach
    void setUp() {
        deletedBlockRepository = mock(DeletedBlockRepository.class);
        scriptEditionRepository = mock(ScriptEditionRepository.class);
        blockService = mock(BlockService.class);
        projectService = mock(ProjectService.class);
        service = new BlockTrashServiceImpl(
                deletedBlockRepository, scriptEditionRepository, blockService, projectService);
        ReflectionTestUtils.setField(service, "retentionDays", 30);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("The Big Musical");

        user = new User();
        user.setId(3);
        user.setUsername("writer");

        record = new DeletedBlock();
        record.setId(DELETED_BLOCK_ID);
        record.setProject(project);
        record.setDeletedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        record.setOriginalOrder(2);
        record.setContent("A line worth keeping");
        record.setType(Block.TYPE_ACTION);

        when(projectService.read(PROJECT_ID)).thenReturn(project);
        when(projectService.canUserAccessProject(project, user)).thenReturn(true);
        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(true);
    }

    @Test
    void trashViewListsDeletedBlocksWithPurgeDate() {
        when(scriptEditionRepository.countByProjectId(PROJECT_ID)).thenReturn(1L);
        when(deletedBlockRepository.findByProjectIdOrderByDeletedAtDesc(PROJECT_ID))
                .thenReturn(List.of(record));

        DeletedBlockListViewModel vm = service.getTrashViewModel(PROJECT_ID, user);

        assertNotNull(vm);
        assertEquals(1, vm.getBlocks().size());
        DeletedBlockViewModel row = vm.getBlocks().get(0);
        assertEquals("A line worth keeping", row.getPreview());
        assertEquals("Action", row.getTypeLabel());
        assertFalse(row.isEmpty());
        // 30-day window from the delete timestamp.
        assertEquals(LocalDateTime.of(2026, 1, 31, 9, 0), row.getPurgeAt());
        // A single-edition project doesn't clutter rows with the edition name.
        assertNull(row.getEditionName());
    }

    @Test
    void trashViewShowsEditionNameWhenProjectHasMoreThanOne() {
        ScriptEdition edition = new ScriptEdition();
        edition.setName("Workshop Draft");
        record.setScriptEdition(edition);
        when(scriptEditionRepository.countByProjectId(PROJECT_ID)).thenReturn(2L);
        when(deletedBlockRepository.findByProjectIdOrderByDeletedAtDesc(PROJECT_ID))
                .thenReturn(List.of(record));

        DeletedBlockListViewModel vm = service.getTrashViewModel(PROJECT_ID, user);

        assertEquals("Workshop Draft", vm.getBlocks().get(0).getEditionName());
    }

    @Test
    void trashViewIsNullWhenProjectNotAccessible() {
        when(projectService.canUserAccessProject(project, user)).thenReturn(false);

        assertNull(service.getTrashViewModel(PROJECT_ID, user));
    }

    @Test
    void restoreRecreatesBlockAndDropsTheRecord() {
        when(deletedBlockRepository.findByIdAndProjectId(DELETED_BLOCK_ID, PROJECT_ID))
                .thenReturn(Optional.of(record));
        Block restored = new Block();
        when(blockService.restoreBlock(record)).thenReturn(restored);

        Block result = service.restore(DELETED_BLOCK_ID, PROJECT_ID, user);

        assertEquals(restored, result);
        verify(blockService).restoreBlock(record);
        verify(deletedBlockRepository).delete(record);
    }

    @Test
    void restoreKeepsRecordWhenRebuildFails() {
        when(deletedBlockRepository.findByIdAndProjectId(DELETED_BLOCK_ID, PROJECT_ID))
                .thenReturn(Optional.of(record));
        when(blockService.restoreBlock(record)).thenReturn(null);

        assertNull(service.restore(DELETED_BLOCK_ID, PROJECT_ID, user));
        verify(deletedBlockRepository, never()).delete(any());
    }

    @Test
    void restoreIsNullWhenRecordMissing() {
        when(deletedBlockRepository.findByIdAndProjectId(DELETED_BLOCK_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertNull(service.restore(DELETED_BLOCK_ID, PROJECT_ID, user));
        verify(blockService, never()).restoreBlock(any());
    }

    @Test
    void restoreIsDeniedWithoutProjectAccess() {
        when(projectService.canUserAccessProject(PROJECT_ID, user)).thenReturn(false);

        assertNull(service.restore(DELETED_BLOCK_ID, PROJECT_ID, user));
        verify(deletedBlockRepository, never()).findByIdAndProjectId(any(), any());
    }

    @Test
    void purgeRemovesTheRecord() {
        when(deletedBlockRepository.findByIdAndProjectId(DELETED_BLOCK_ID, PROJECT_ID))
                .thenReturn(Optional.of(record));

        assertTrue(service.purge(DELETED_BLOCK_ID, PROJECT_ID, user));
        verify(deletedBlockRepository).delete(record);
    }

    @Test
    void purgeIsFalseWhenRecordMissing() {
        when(deletedBlockRepository.findByIdAndProjectId(DELETED_BLOCK_ID, PROJECT_ID))
                .thenReturn(Optional.empty());

        assertFalse(service.purge(DELETED_BLOCK_ID, PROJECT_ID, user));
        verify(deletedBlockRepository, never()).delete(any());
    }

    @Test
    void purgeExpiredRemovesEverythingPastTheWindow() {
        List<DeletedBlock> expired = List.of(record, new DeletedBlock());
        when(deletedBlockRepository.findByDeletedAtBefore(any())).thenReturn(expired);

        assertEquals(2, service.purgeExpired());
        verify(deletedBlockRepository).deleteAll(expired);
    }

    @Test
    void purgeExpiredKeepsEverythingWhenRetentionIsUnlimited() {
        ReflectionTestUtils.setField(service, "retentionDays", 0);

        assertEquals(0, service.purgeExpired());
        verify(deletedBlockRepository, never()).findByDeletedAtBefore(any());
        verify(deletedBlockRepository, never()).deleteAll(any());
    }
}
