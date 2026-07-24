package com.scripty.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.api.BlockResourceAssembler;
import com.scripty.api.BulkFormatRequest;
import com.scripty.repository.BlockRepository;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.BlockService;
import com.scripty.service.ProjectUndoRedoService;
import com.scripty.service.ProjectVersionService;
import com.scripty.service.ScriptEditionService;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The bulk-format endpoint's font handling, and in particular resetting a font
 * to the default. The lenient MVC endpoint lets a blank font mean "clear", but
 * this REST endpoint rejects an unrecognised font, so clearing rides its own
 * {@code clearFont} flag — the same shape {@code clearHighlight} already uses.
 */
class BlockRestControllerBulkFontTest {

    private final BlockRestController controller = new BlockRestController();
    private final BlockService blockService = mock(BlockService.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final BlockResourceAssembler assembler = mock(BlockResourceAssembler.class);
    private final ProjectAccessSupport projectAccess = mock(ProjectAccessSupport.class);
    private final ScriptEditionService scriptEditionService = mock(ScriptEditionService.class);
    private final ProjectUndoRedoService projectUndoRedoService = mock(ProjectUndoRedoService.class);
    private final ProjectVersionService projectVersionService = mock(ProjectVersionService.class);
    private final Principal principal = () -> "writer";

    @BeforeEach
    void setUp() {
        controller.blockService = blockService;
        controller.blockRepository = blockRepository;
        controller.blockResourceAssembler = assembler;
        controller.projectAccess = projectAccess;
        controller.scriptEditionService = scriptEditionService;
        controller.projectUndoRedoService = projectUndoRedoService;
        controller.projectVersionService = projectVersionService;
    }

    /** Everything the collection-refreshing `list` call needs to succeed. */
    private void allowEdit() {
        when(projectAccess.canEditScript(7, principal)).thenReturn(true);
        when(projectAccess.canEditBlocks(any(), eq(7), any())).thenReturn(true);
        when(projectAccess.canAccessProject(7, principal)).thenReturn(true);
        when(scriptEditionService.resolveForAccess(anyInt(), any(), anyBoolean())).thenReturn(null);
        when(blockRepository.findByProjectIdOrderByOrderAscIdAsc(7)).thenReturn(List.of());
        when(assembler.toBlockCollection(any(), eq(7))).thenReturn(CollectionModel.empty());
    }

    @Test
    void clearFontResetsTheFontToNull() {
        allowEdit();
        BulkFormatRequest request = new BulkFormatRequest(
                Arrays.asList(3, 4), 7, null, null, null, null, null, true);

        ResponseEntity<?> response = controller.bulkFormat(request, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // The clear routes to the service with a null font, which is how the
        // service knows to drop the override rather than store a value.
        verify(blockService).setBlockFonts(eq(Arrays.asList(3, 4)), isNull());
    }

    @Test
    void clearFontOnlyIsNotAnEmptyRequest() {
        allowEdit();
        BulkFormatRequest request = new BulkFormatRequest(
                Arrays.asList(3), 7, null, null, null, null, null, true);

        ResponseEntity<?> response = controller.bulkFormat(request, principal);

        // clearFont alone is a real change, not the "supply at least one field" no-op.
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void anUnrecognisedFontIsStillRejected() {
        BulkFormatRequest request = new BulkFormatRequest(
                Arrays.asList(3), 7, null, "Comic Sans", null, null, null, null);
        when(projectAccess.canEditScript(7, principal)).thenReturn(true);
        when(projectAccess.canEditBlocks(any(), eq(7), any())).thenReturn(true);

        ResponseEntity<?> response = controller.bulkFormat(request, principal);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Font must be one of Courier Prime, Arial, or Times New Roman.",
                ((Map<?, ?>) response.getBody()).get("font"));
        verify(blockService, never()).setBlockFonts(any(), any());
    }

    @Test
    void requestFlagsReflectClearFont() {
        BulkFormatRequest clearing = new BulkFormatRequest(
                Arrays.asList(3), 7, null, null, null, null, null, true);
        assertTrue(clearing.hasFont());
        assertNull(clearing.resolvedFont());
        assertFalse(clearing.isEmpty());

        BulkFormatRequest setting = new BulkFormatRequest(
                Arrays.asList(3), 7, null, "ARIAL", null, null, null, null);
        assertTrue(setting.hasFont());
        assertEquals("ARIAL", setting.resolvedFont());

        BulkFormatRequest leaving = new BulkFormatRequest(
                Arrays.asList(3), 7, null, null, null, null, null, null);
        assertFalse(leaving.hasFont());
    }
}
