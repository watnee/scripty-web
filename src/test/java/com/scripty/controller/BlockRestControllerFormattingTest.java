package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.BlockResource;
import com.scripty.api.BlockResourceAssembler;
import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
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
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

class BlockRestControllerFormattingTest {

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

    private EditBlockCommandModel command() {
        EditBlockCommandModel cmd = new EditBlockCommandModel();
        cmd.setContent("He opens the door.");
        return cmd;
    }

    private BindingResult bindingFor(EditBlockCommandModel cmd) {
        return new BeanPropertyBindingResult(cmd, "commandModel");
    }

    private void allowEdit() {
        Project project = new Project();
        project.setId(7);
        Block block = new Block();
        block.setId(3);
        block.setProject(project);

        when(projectAccess.canEditBlock(3, principal)).thenReturn(true);
        when(blockService.read(3)).thenReturn(block);
        when(blockService.saveEditBlockCommandModel(any())).thenReturn(block);
        when(assembler.toModel(block)).thenReturn(EntityModel.of(new BlockResource()));
    }

    @Test
    void acceptsFormattingAndPassesItToTheService() {
        allowEdit();
        EditBlockCommandModel cmd = command();
        cmd.setTextAlign("center");
        cmd.setFont("Courier Prime");
        cmd.setTextBold(true);

        ResponseEntity<?> response = controller.update(3, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(blockService).saveEditBlockCommandModel(cmd);
    }

    @Test
    void omittedFormattingStillSaves() {
        allowEdit();
        EditBlockCommandModel cmd = command();

        ResponseEntity<?> response = controller.update(3, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(blockService).saveEditBlockCommandModel(cmd);
    }

    @Test
    void rejectsUnknownAlignment() {
        EditBlockCommandModel cmd = command();
        cmd.setTextAlign("justify");

        ResponseEntity<?> response = controller.update(3, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Text align must be one of left, center, or right.",
                ((Map<?, ?>) response.getBody()).get("textAlign"));
        verify(blockService, never()).saveEditBlockCommandModel(any());
    }

    @Test
    void rejectsUnknownFont() {
        EditBlockCommandModel cmd = command();
        cmd.setFont("Comic Sans");

        ResponseEntity<?> response = controller.update(3, cmd, bindingFor(cmd), principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Font must be one of Courier Prime, Arial, or Times New Roman.",
                ((Map<?, ?>) response.getBody()).get("font"));
        verify(blockService, never()).saveEditBlockCommandModel(any());
    }
}
