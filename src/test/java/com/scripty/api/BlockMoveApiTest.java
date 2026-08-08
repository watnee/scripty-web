package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ScriptEditionService;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Move Up and Move Down against a real database.
 *
 * <p>Against a mock repository the move is four statements and a return; the
 * thing that breaks it only exists once there is a persistence context to
 * clear. {@code incrementOrdersInRange} and its twin renumber the script in one
 * statement each and clear the context after, which detaches every entity read
 * before them. The block carried out of the service was one of those, and its
 * {@code person} was still an untouched proxy — so the resource assembler,
 * running after the transaction with {@code open-in-view} off, threw
 * {@code LazyInitializationException} and the move answered 500. Only a
 * character cue has a person, which is why an action line moved perfectly well
 * and the writer's dialogue did not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockMoveApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Autowired
    private BlockRepository blockRepository;

    @MockBean
    private ProjectAccessSupport projectAccess;

    @Autowired
    private PersonRepository personRepository;

    private Integer projectId;
    private ScriptEdition edition;
    private List<Block> blocks;

    @BeforeEach
    void setUp() {
        when(projectAccess.canEditBlock(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);

        Project project = new Project();
        project.setTitle("Move test");
        project = projectRepository.save(project);
        projectId = project.getId();

        edition = scriptEditionService.ensureDefaultEdition(projectId);

        for (int i = 1; i <= 3; i++) {
            Block block = new Block();
            block.setOrder(i);
            block.setContent("Line " + i);
            block.setType(Block.TYPE_ACTION);
            block.setProject(project);
            block.setScriptEdition(edition);
            blockRepository.save(block);
        }
        blocks = blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId());
    }

    @Test
    void moveUpAnswersTheMovedBlock() throws Exception {
        Block second = blocks.get(1);
        Block first = blocks.get(0);

        mockMvc.perform(post("/api/block/" + second.getId() + "/move")
                        .with(user("admin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":" + first.getOrder() + "}"))
                .andExpect(status().isOk());
    }

    /**
     * A script that has been edited for a while: past the auto-save retention
     * cap, so every further move also prunes.
     */
    @Test
    void moveKeepsWorkingOnceTheAutoSaveCapIsPassed() throws Exception {
        Block second = blocks.get(1);
        Block first = blocks.get(0);
        Block third = blocks.get(2);

        for (int i = 0; i < 40; i++) {
            Block target = i % 2 == 0 ? third : first;
            mockMvc.perform(post("/api/block/" + second.getId() + "/move")
                            .with(user("admin").roles("USER")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"position\":" + target.getOrder() + "}"))
                    .andExpect(status().isOk());
        }
    }

    /**
     * The realistic script: the block being moved is a character cue, so the
     * answer has to reach through the block's {@code person}.
     */
    @Test
    void aCharacterCueCanBeMoved() throws Exception {
        Person person = new Person();
        person.setName("MARGO");
        person.setFullName("MARGO");
        person.setProject(projectRepository.findById(projectId).orElseThrow());
        person.setScriptEdition(edition);
        person = personRepository.save(person);

        Block cue = new Block();
        cue.setOrder(4);
        cue.setContent("MARGO");
        cue.setType(Block.TYPE_CHARACTER);
        cue.setProject(projectRepository.findById(projectId).orElseThrow());
        cue.setScriptEdition(edition);
        cue.setPerson(person);
        cue = blockRepository.save(cue);

        mockMvc.perform(post("/api/block/" + cue.getId() + "/move")
                        .with(user("admin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personName").value("MARGO"))
                .andExpect(jsonPath("$.order").value(2));
    }

    @Test
    void moveDownAnswersTheMovedBlock() throws Exception {
        Block second = blocks.get(1);
        Block third = blocks.get(2);

        mockMvc.perform(post("/api/block/" + second.getId() + "/move")
                        .with(user("admin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":" + third.getOrder() + "}"))
                .andExpect(status().isOk());
    }
}
