package com.scripty.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.scripty.dto.Project;
import com.scripty.dto.TextDocument;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.TextDocumentRepository;
import com.scripty.repository.UserRepository;
import java.security.Principal;
import java.util.ArrayList;
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

/** Songs &amp; notes Move Up / Move Down against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentReorderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TextDocumentRepository textDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private com.scripty.security.ProjectAccessSupport projectAccess;

    private Integer projectId;
    private List<Integer> documentIds;

    @BeforeEach
    void setUp() {
        when(projectAccess.canEditScript(anyInt(), any(Principal.class))).thenReturn(true);
        when(projectAccess.canAccessProject(anyInt(), any(Principal.class))).thenReturn(true);
        com.scripty.dto.User writer = userRepository.findByUsername("admin").orElseThrow();
        when(projectAccess.currentUser(any(Principal.class))).thenReturn(writer);

        Project project = new Project();
        project.setTitle("Reorder test");
        project = projectRepository.save(project);
        projectId = project.getId();

        documentIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TextDocument document = new TextDocument();
            document.setProject(project);
            document.setTitle("Song " + i);
            document.setDocumentType(TextDocument.TYPE_SONG);
            document.setContent("La la la");
            document.setSortOrder(i);
            document.setCreatedAt(java.time.LocalDateTime.now());
            document.setUpdatedAt(java.time.LocalDateTime.now());
            documentIds.add(textDocumentRepository.save(document).getId());
        }
    }

    @Test
    void movingASongUpIsAccepted() throws Exception {
        List<Integer> moved = List.of(documentIds.get(1), documentIds.get(0), documentIds.get(2));

        mockMvc.perform(post("/api/document/reorder").param("projectId", String.valueOf(projectId))
                        .with(user("admin").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedIds\":[" + moved.get(0) + "," + moved.get(1) + ","
                                + moved.get(2) + "]}"))
                .andExpect(status().isOk());
    }
}
