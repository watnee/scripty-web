package com.scripty.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.UserRepository;
import com.scripty.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the rendered HAL payload of {@link ProjectResourceAssembler} through the
 * real HTTP layer, which the assembler's unit-level callers never exercise.
 *
 * <p>Two things are load-bearing here and break independently. The script-import
 * link is gated on {@code canEditScript}, so a reader who could not use the
 * endpoint must never be told it exists; and the self link carries update and
 * delete affordances that surface as HAL-FORMS templates, which a careless edit
 * to the link builder would silently drop.
 *
 * <p>Note the role flags on {@link User} are {@code @Transient} — they are
 * derived from the {@code authority} table. Users therefore have to be created
 * through {@link UserService}, which writes those rows; saving the entity
 * directly would produce a user with no roles at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectResourceLinkGatingIntegrationTest {

    private static final String WRITER = "gating_writer";
    private static final String READER = "gating_reader";

    /** Custom rels are namespaced by the curie provider (see HypermediaConfig). */
    private static final String IMPORT_SCRIPT_REL = "$._links.['scripty:importScript']";
    /** Team management is editor-only, gated the same way as script import. */
    private static final String PROJECT_TEAMS_REL = "$._links.['scripty:projectTeams']";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private Integer projectId;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setTitle("Link Gating Fixture");
        projectId = projectRepository.save(project).getId();

        // A writer may edit the screenplay. An actor has project-wide read
        // access but is not a writer, which is precisely the read-only caller
        // that must not be shown the import link.
        createUser(WRITER, true, false);
        createUser(READER, false, true);
    }

    private void createUser(String username, boolean writer, boolean actor) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword("irrelevant");
        user.setFirstName("Link");
        user.setLastName("Gating");
        user.setEnabled(true);
        user.setWriter(writer);
        user.setActor(actor);
        userService.create(user);
    }

    @Test
    void writerIsOfferedTheScriptImportLink() throws Exception {
        mockMvc.perform(get("/api/project/" + projectId)
                        .accept(MediaTypes.HAL_FORMS_JSON)
                        .with(user(WRITER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(IMPORT_SCRIPT_REL).exists())
                .andExpect(jsonPath(IMPORT_SCRIPT_REL + ".href",
                        org.hamcrest.Matchers.containsString("/api/project/" + projectId + "/import-script")));
    }

    /**
     * The affordances main renders on the self link have to survive alongside
     * the gated link, since both are assembled in the same place.
     */
    @Test
    void writerPayloadStillCarriesTheUpdateAndDeleteAffordances() throws Exception {
        mockMvc.perform(get("/api/project/" + projectId)
                        .accept(MediaTypes.HAL_FORMS_JSON)
                        .with(user(WRITER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._templates.update.method").value("PUT"))
                .andExpect(jsonPath("$._templates.delete.method").value("DELETE"))
                // The update template exposes the title-page fields this commit
                // added, so the affordance describes the real request body.
                .andExpect(jsonPath("$._templates.update.properties[?(@.name == 'screenplayTitle')]").exists())
                .andExpect(jsonPath("$._templates.update.properties[?(@.name == 'writers')]").exists());
    }

    @Test
    void writerIsOfferedTheProjectTeamsLink() throws Exception {
        mockMvc.perform(get("/api/project/" + projectId)
                        .accept(MediaTypes.HAL_FORMS_JSON)
                        .with(user(WRITER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PROJECT_TEAMS_REL).exists())
                .andExpect(jsonPath(PROJECT_TEAMS_REL + ".href",
                        org.hamcrest.Matchers.containsString("/api/project/" + projectId + "/teams")));
    }

    @Test
    void readOnlyUserIsNotOfferedTheProjectTeamsLink() throws Exception {
        mockMvc.perform(get("/api/project/" + projectId)
                        .accept(MediaTypes.HAL_FORMS_JSON)
                        .with(user(READER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PROJECT_TEAMS_REL).doesNotExist());
    }

    @Test
    void readOnlyUserIsNotOfferedTheScriptImportLink() throws Exception {
        mockMvc.perform(get("/api/project/" + projectId)
                        .accept(MediaTypes.HAL_FORMS_JSON)
                        .with(user(READER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath(IMPORT_SCRIPT_REL).doesNotExist());
    }

    /**
     * Withholding the import link must not cost the reader anything else: the
     * gate is meant to remove exactly one link.
     */
    @Test
    void readOnlyPayloadIsOtherwiseUnaffected() throws Exception {
        mockMvc.perform(get("/api/project/" + projectId)
                        .accept(MediaTypes.HAL_FORMS_JSON)
                        .with(user(READER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.title").value("Link Gating Fixture"))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.['scripty:projects'].href").exists())
                .andExpect(jsonPath("$._links.['scripty:blocks'].href").exists())
                .andExpect(jsonPath("$._links.['scripty:export'].href").exists())
                .andExpect(jsonPath("$._templates.update.method").value("PUT"))
                .andExpect(jsonPath("$._templates.delete.method").value("DELETE"));
    }
}
