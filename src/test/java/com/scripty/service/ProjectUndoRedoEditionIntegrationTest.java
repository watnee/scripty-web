package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards the edition-key contract for screenplay undo/redo against a real
 * database: a checkpoint recorded with a null edition (bulk delete, inline
 * create, and session-less API clients all do this) must be visible to the web
 * editor, which sends the resolved default-edition id on undo. Before the
 * canonicalization fix these keyed different rows, so undo silently restored
 * nothing.
 *
 * <p>Not {@code @Transactional}: {@code recordCheckpoint} snapshots in a
 * REQUIRES_NEW transaction, which only sees committed blocks, so each step must
 * commit independently — exactly as it does across separate HTTP requests.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectUndoRedoEditionIntegrationTest {

    @Autowired
    private ProjectUndoRedoService undoRedoService;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private BlockRepository blockRepository;

    @BeforeEach
    void authenticateAsAdmin() {
        // A resolvable user persists the stacks in project_undo_state; without
        // one the service falls back to a request-scoped session that does not
        // exist in this test, and nothing would survive between service calls.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private Integer createBlock(Project project, ScriptEdition edition, String content) {
        Block block = new Block();
        block.setOrder(1);
        block.setContent(content);
        block.setType(Block.TYPE_ACTION);
        block.setProject(project);
        block.setScriptEdition(edition);
        return blockRepository.save(block).getId();
    }

    @Test
    void nullEditionCheckpointIsRestorableByDefaultEditionId() {
        Project project = new Project();
        project.setTitle("Undo Edition Key");
        Integer projectId = projectRepository.save(project).getId();
        ScriptEdition edition = scriptEditionService.ensureDefaultEdition(projectId);
        Integer editionId = edition.getId();

        Integer blockId = createBlock(project, edition, "RESTORE ME");

        // Mirror BlockController#bulkDelete: checkpoint under a null edition,
        // then hard-delete the block.
        undoRedoService.recordCheckpoint(projectId);
        blockRepository.deleteById(blockId);
        assertTrue(blockRepository.findByScriptEditionIdOrderByOrderAsc(editionId).isEmpty());

        // The web editor asks with the concrete default-edition id, not null.
        assertTrue(undoRedoService.canUndo(projectId, editionId),
                "a null-edition checkpoint must be visible under the default edition id");

        ProjectUndoRedoService.UndoRedoResult result =
                undoRedoService.undoWithDetails(projectId, editionId);
        assertTrue(result.success(), "undo must succeed for the deleted block");

        List<Block> restored = blockRepository.findByScriptEditionIdOrderByOrderAsc(editionId);
        assertEquals(1, restored.size(), "the deleted block must be restored");
        assertEquals("RESTORE ME", restored.get(0).getContent());
    }
}
