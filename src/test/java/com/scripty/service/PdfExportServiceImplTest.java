package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PdfExportServiceImplTest {

    @Test
    void exportsFirstBlockWhenTitlePagePresent() throws Exception {
        String text = exportText(project(true), List.of(
                block(Block.TYPE_SCENE, "INT. KITCHEN - DAY"),
                block(Block.TYPE_ACTION, "Jane enters."),
                block(Block.TYPE_ACTION, "She sits down.")));

        assertTrue(text.contains("MY SCRIPT"), "title page missing; got:\n" + text);
        assertTrue(text.contains("INT. KITCHEN - DAY"), "FIRST BLOCK MISSING; got:\n" + text);
        assertTrue(text.contains("Jane enters."), "second block missing; got:\n" + text);
        assertTrue(text.contains("She sits down."), "third block missing; got:\n" + text);
    }

    @Test
    void exportsFirstBlockWithoutTitlePage() throws Exception {
        String text = exportText(project(false), List.of(
                block(Block.TYPE_SCENE, "INT. KITCHEN - DAY"),
                block(Block.TYPE_ACTION, "Jane enters.")));

        assertTrue(text.contains("INT. KITCHEN - DAY"), "FIRST BLOCK MISSING; got:\n" + text);
        assertTrue(text.contains("Jane enters."), "second block missing; got:\n" + text);
    }

    @Test
    void rendersEveryPrintableTypeAsTheFirstBlock() throws Exception {
        for (String type : List.of(Block.TYPE_SCENE, Block.TYPE_ACTION, Block.TYPE_TEXT,
                Block.TYPE_CENTERED, Block.TYPE_TRANSITION)) {
            String text = exportText(project(true), List.of(
                    block(type, "FIRSTBLOCKMARKER"),
                    block(Block.TYPE_ACTION, "Second block.")));
            assertTrue(text.contains("FIRSTBLOCKMARKER"),
                    "first block of type " + type + " missing; got:\n" + text);
        }
    }

    @Test
    void omitsOutlineTypesAsTheFirstBlock() throws Exception {
        // SECTION/SYNOPSIS/NOTE are outline metadata, hidden by print CSS and DOCX export too.
        for (String type : List.of(Block.TYPE_SECTION, Block.TYPE_SYNOPSIS, Block.TYPE_NOTE)) {
            String text = exportText(project(true), List.of(
                    block(type, "FIRSTBLOCKMARKER"),
                    block(Block.TYPE_ACTION, "Second block.")));
            assertFalse(text.contains("FIRSTBLOCKMARKER"),
                    "outline type " + type + " should not print; got:\n" + text);
            assertTrue(text.contains("Second block."), "body after outline block missing");
        }
    }

    private static String exportText(Project project, List<Block> blocks) throws Exception {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        BlockRepository blockRepository = mock(BlockRepository.class);
        ScriptEditionService scriptEditionService = mock(ScriptEditionService.class);

        when(projectRepository.findById(1)).thenReturn(Optional.of(project));
        when(scriptEditionService.requireForProject(1, null)).thenReturn(null);
        when(blockRepository.findByProjectIdOrderByOrderAsc(1)).thenReturn(blocks);

        PdfExportServiceImpl service = new PdfExportServiceImpl();
        ReflectionTestUtils.setField(service, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(service, "blockRepository", blockRepository);
        ReflectionTestUtils.setField(service, "scriptEditionService", scriptEditionService);

        byte[] pdf = service.exportProject(1);
        try (PDDocument doc = Loader.loadPDF(new org.apache.pdfbox.io.RandomAccessReadBuffer(
                new ByteArrayInputStream(pdf)))) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static Project project(boolean withTitlePage) {
        Project p = new Project();
        p.setTitle("Demo");
        if (withTitlePage) {
            p.setScreenplayTitle("My Script");
            p.setWriters("written by\nJane Doe");
        }
        return p;
    }

    private static Block block(String type, String content) {
        Block b = new Block();
        b.setType(type);
        b.setContent(content);
        return b;
    }
}
