package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.BlockResource;
import com.scripty.api.BlockResourceAssembler;
import com.scripty.api.ReplaceOccurrenceRequest;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Single-occurrence replace on one block — the REST twin of the MVC editor's
 * {@code /block/replaceOne}, driving the "Replace" that sits beside "Replace
 * All" in find-and-replace.
 */
class BlockRestControllerReplaceTest {

    private final BlockRestController controller = new BlockRestController();
    private final BlockService blockService = mock(BlockService.class);
    private final BlockResourceAssembler assembler = mock(BlockResourceAssembler.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final ProjectUndoRedoService projectUndoRedoService = mock(ProjectUndoRedoService.class);
    private final ProjectVersionService projectVersionService = mock(ProjectVersionService.class);
    private final Principal principal = () -> "writer";

    @BeforeEach
    void setUp() {
        controller.blockService = blockService;
        controller.blockResourceAssembler = assembler;
        controller.projectAccess = projectAccess;
        controller.projectUndoRedoService = projectUndoRedoService;
        controller.projectVersionService = projectVersionService;
    }

    private Block block() {
        Project project = new Project();
        project.setId(7);
        Block block = new Block();
        block.setId(3);
        block.setProject(project);
        return block;
    }

    private void allowEdit(Block block) {
        when(projectAccess.canEditBlock(3, principal)).thenReturn(true);
        when(blockService.read(3)).thenReturn(block);
        when(assembler.toModel(any(Block.class))).thenReturn(EntityModel.of(new BlockResource()));
    }

    private ReplaceOccurrenceRequest request(String find, int occurrence) {
        return new ReplaceOccurrenceRequest(find, "cat", false, false, occurrence);
    }

    @Test
    void replacesTheNamedOccurrenceAndCheckpoints() {
        Block block = block();
        allowEdit(block);
        when(blockService.replaceOccurrenceInBlock(3, "dog", "cat", false, false, 1)).thenReturn(block);

        ResponseEntity<?> response = controller.replace(3, request("dog", 1), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(projectUndoRedoService).recordCheckpoint(7);
        verify(blockService).replaceOccurrenceInBlock(3, "dog", "cat", false, false, 1);
        verify(projectVersionService).autoSaveVersionForBlock(3);
    }

    @Test
    void aMissingOccurrenceDefaultsToTheFirst() {
        Block block = block();
        allowEdit(block);
        when(blockService.replaceOccurrenceInBlock(3, "dog", "cat", false, false, 0)).thenReturn(block);

        ResponseEntity<?> response = controller.replace(
                3, new ReplaceOccurrenceRequest("dog", "cat", null, null, null), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(blockService).replaceOccurrenceInBlock(3, "dog", "cat", false, false, 0);
    }

    @Test
    void aNoOpOccurrenceStillReturnsTheBlockWithoutAutosaving() {
        Block block = block();
        allowEdit(block);
        when(blockService.replaceOccurrenceInBlock(eq(3), any(), any(), anyBool(), anyBool(), anyInt()))
                .thenReturn(null);

        ResponseEntity<?> response = controller.replace(3, request("dog", 9), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Nothing changed, so no version is cut, but the caller still gets state.
        verify(projectVersionService, never()).autoSaveVersionForBlock(anyInt());
    }

    @Test
    void rejectsAnEmptyFind() {
        when(projectAccess.canEditBlock(3, principal)).thenReturn(true);

        ResponseEntity<?> response = controller.replace(
                3, new ReplaceOccurrenceRequest("", "cat", false, false, 0), principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("You must supply a value to find.",
                ((Map<?, ?>) response.getBody()).get("find"));
        verify(blockService, never()).replaceOccurrenceInBlock(
                anyInt(), any(), any(), anyBool(), anyBool(), anyInt());
    }

    @Test
    void forbidsANonEditor() {
        when(projectAccess.canEditBlock(3, principal)).thenReturn(false);

        ResponseEntity<?> response = controller.replace(3, request("dog", 0), principal);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(blockService, never()).replaceOccurrenceInBlock(
                anyInt(), any(), any(), anyBool(), anyBool(), anyInt());
    }

    private static boolean anyBool() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
