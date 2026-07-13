package com.scripty.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlockRepositoryTrashIntegrationTest {

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Project savedProject() {
        Project project = new Project();
        project.setTitle("Trash Test");
        return projectRepository.save(project);
    }

    private Block savedBlock(Project project, int order) {
        Block block = new Block();
        block.setProject(project);
        block.setOrder(order);
        block.setContent("line " + order);
        block.setType(Block.TYPE_ACTION);
        block.setBookmarked(false);
        block.setPinned(false);
        return blockRepository.save(block);
    }

    @Test
    void deleteSoftDeletesAndHidesBlockFromQueries() {
        Project project = savedProject();
        Block block = savedBlock(project, 1);
        Integer id = block.getId();

        blockRepository.delete(block);
        blockRepository.flush();

        assertTrue(blockRepository.findById(id).isEmpty(), "soft-deleted block must be hidden");
        assertEquals(0, blockRepository.countByProjectId(project.getId()));
        assertTrue(blockRepository.findDeletedById(id).isPresent(), "block must be in the trash");

        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Block> trash = blockRepository.findDeletedByProjectIdSince(project.getId(), cutoff);
        assertEquals(1, trash.size());
        assertEquals(id, trash.get(0).getId());
    }

    @Test
    void restoreDeletedByIdBringsBlockBack() {
        Project project = savedProject();
        Block block = savedBlock(project, 1);
        Integer id = block.getId();
        blockRepository.delete(block);
        blockRepository.flush();

        int restored = blockRepository.restoreDeletedById(id, 1);

        assertEquals(1, restored);
        assertTrue(blockRepository.findById(id).isPresent(), "restored block must be visible again");
        assertFalse(blockRepository.findDeletedById(id).isPresent());
    }

    @Test
    void purgeRemovesOnlyBlocksPastRetention() {
        Project project = savedProject();
        Block oldBlock = savedBlock(project, 1);
        Block recentBlock = savedBlock(project, 2);
        blockRepository.delete(oldBlock);
        blockRepository.delete(recentBlock);
        blockRepository.flush();
        jdbcTemplate.update("UPDATE `block` SET deleted_at = DATEADD('DAY', -31, CURRENT_TIMESTAMP) WHERE id = ?",
                oldBlock.getId());

        int purged = blockRepository.purgeDeletedBefore(Instant.now().minus(30, ChronoUnit.DAYS));

        assertEquals(1, purged);
        assertTrue(blockRepository.findDeletedById(oldBlock.getId()).isEmpty(), "expired block must be gone");
        assertTrue(blockRepository.findDeletedById(recentBlock.getId()).isPresent(),
                "recently deleted block must survive the purge");
    }
}
