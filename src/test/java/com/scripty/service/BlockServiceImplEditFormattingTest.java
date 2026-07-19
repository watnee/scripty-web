package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.scripty.commandmodel.block.editblock.EditBlockCommandModel;
import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.DeletedBlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockServiceImplEditFormattingTest {

    @Mock
    private BlockRepository blockRepository;
    @Mock
    private DeletedBlockRepository deletedBlockRepository;
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
    @Mock
    private UserService userService;

    private BlockServiceImpl service;
    private Block block;

    @BeforeEach
    void setUp() {
        service = new BlockServiceImpl(
                blockRepository,
                deletedBlockRepository,
                personRepository,
                projectRepository,
                projectActivityService,
                projectUndoRedoService,
                scriptEditionService,
                userService);

        Project project = new Project();
        project.setId(7);

        block = new Block();
        block.setId(3);
        block.setType(Block.TYPE_ACTION);
        block.setProject(project);
        block.setContent("He walks to the door.");
        block.setTextAlign(Block.ALIGN_CENTER);
        block.setFont(Block.FONT_ARIAL);
        block.setTextBold(true);
        block.setTextItalic(true);
        block.setTextUnderline(true);

        when(blockRepository.findById(3)).thenReturn(Optional.of(block));
    }

    private EditBlockCommandModel command(String content) {
        EditBlockCommandModel cmd = new EditBlockCommandModel();
        cmd.setId(3);
        cmd.setContent(content);
        return cmd;
    }

    @Test
    void contentOnlySaveLeavesFormattingUntouched() {
        // The debounced auto-save (and the MVC edit form) send content without
        // formatting; that must not wipe what the writer already styled.
        service.saveEditBlockCommandModel(command("He opens the door."));

        assertEquals(Block.ALIGN_CENTER, block.getTextAlign());
        assertEquals(Block.FONT_ARIAL, block.getFont());
        assertTrue(block.isTextBold());
        assertTrue(block.isTextItalic());
        assertTrue(block.isTextUnderline());
    }

    @Test
    void suppliedFormattingIsStoredInCanonicalForm() {
        EditBlockCommandModel cmd = command("He opens the door.");
        cmd.setTextAlign("right");
        cmd.setFont("Times New Roman");
        cmd.setTextBold(false);
        cmd.setTextUnderline(false);

        service.saveEditBlockCommandModel(cmd);

        assertEquals(Block.ALIGN_RIGHT, block.getTextAlign());
        assertEquals(Block.FONT_TIMES_NEW_ROMAN, block.getFont());
        assertFalse(block.isTextBold());
        assertFalse(block.isTextUnderline());
        // Not supplied, so unchanged.
        assertTrue(block.isTextItalic());
    }
}
