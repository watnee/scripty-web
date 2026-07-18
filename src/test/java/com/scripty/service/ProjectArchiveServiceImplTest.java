package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Block;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.dto.TextDocument;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ScriptEditionRepository;
import com.scripty.repository.TextDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProjectArchiveServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ScriptEditionRepository scriptEditionRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private TextDocumentRepository textDocumentRepository;
    @Mock
    private ScriptEditionService scriptEditionService;
    @Mock
    private ProjectVersionService projectVersionService;
    @Mock
    private ProjectActivityService projectActivityService;

    @InjectMocks
    private ProjectArchiveServiceImpl service;

    private final AtomicInteger idSequence = new AtomicInteger(100);

    @Test
    void exportProjectSerializesFullProject() {
        Project project = new Project();
        project.setId(7);
        project.setTitle("My Musical");
        project.setScreenplayTitle("MY MUSICAL");
        project.setWriters("Jane Doe");

        ScriptEdition edition = new ScriptEdition();
        edition.setId(3);
        edition.setName("Original");
        edition.setDefault(true);
        edition.setPublished(true);
        edition.setProject(project);

        Person person = new Person();
        person.setId(11);
        person.setName("ALICE");
        person.setFullName("Alice Smith");
        person.setScriptEdition(edition);

        TextDocument document = new TextDocument();
        document.setId(21);
        document.setTitle("Opening Song");
        document.setDocumentType(TextDocument.TYPE_SONG);
        document.setContent("La la la");
        document.setSortOrder(0);

        Block scene = new Block();
        scene.setOrder(1);
        scene.setType(Block.TYPE_SCENE);
        scene.setContent("INT. HOUSE - DAY");
        scene.setScriptEdition(edition);

        Block cue = new Block();
        cue.setOrder(2);
        cue.setType(Block.TYPE_CHARACTER);
        cue.setContent("ALICE");
        cue.setScriptEdition(edition);
        cue.setPerson(person);
        cue.setSourceDocumentId(21);

        when(projectRepository.findById(7)).thenReturn(Optional.of(project));
        when(scriptEditionRepository.findByProjectIdOrderByNameAsc(7)).thenReturn(List.of(edition));
        when(personRepository.findByProjectIdOrderByNameAsc(7)).thenReturn(List.of(person));
        when(textDocumentRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAscUpdatedAtDesc(7)).thenReturn(List.of(document));
        when(blockRepository.findByProjectIdOrderByOrderAscIdAsc(7)).thenReturn(List.of(scene, cue));

        String json = new String(service.exportProject(7), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"format\" : \"scripty-project\""));
        assertTrue(json.contains("\"title\" : \"My Musical\""));
        assertTrue(json.contains("\"INT. HOUSE - DAY\""));
        assertTrue(json.contains("\"characterKey\" : 11"));
        assertTrue(json.contains("\"sourceDocumentKey\" : 21"));
        assertTrue(json.contains("\"Opening Song\""));
    }

    @Test
    void exportProjectsBundleRoundTripsThroughImport() {
        Project first = new Project();
        first.setId(7);
        first.setTitle("My Musical");
        Project second = new Project();
        second.setId(8);
        second.setTitle("Second Show");

        when(projectRepository.findById(7)).thenReturn(Optional.of(first));
        when(projectRepository.findById(8)).thenReturn(Optional.of(second));
        // Missing projects are skipped rather than failing the whole download.
        when(projectRepository.findById(99)).thenReturn(Optional.empty());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(idSequence.incrementAndGet());
            }
            return p;
        });

        byte[] bundle = service.exportProjectsBundle(List.of(7, 99, 8));
        String json = new String(bundle, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"format\" : \"scripty-projects\""));
        assertTrue(json.contains("\"My Musical\""));
        assertTrue(json.contains("\"Second Show\""));

        MockMultipartFile file = new MockMultipartFile(
                "file", "scripty-projects-2.scripty.json", "application/json", bundle);
        List<Project> imported = assertDoesNotThrow(() -> service.importProjects(file));

        assertEquals(2, imported.size());
        assertEquals("My Musical", imported.get(0).getTitle());
        assertEquals("Second Show", imported.get(1).getTitle());
    }

    @Test
    void exportProjectsBundleReturnsNullWhenNoProjectResolves() {
        when(projectRepository.findById(99)).thenReturn(Optional.empty());
        assertNull(service.exportProjectsBundle(List.of(99)));
    }

    @Test
    void importProjectsRejectsBundlesFromNewerFormatVersions() {
        String json = "{\"format\":\"scripty-projects\",\"formatVersion\":99,\"projects\":[]}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "p.scripty.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        ScriptImportException e = assertThrows(ScriptImportException.class, () -> service.importProjects(file));
        assertTrue(e.getUserMessage().contains("newer version"));
    }

    @Test
    void importProjectsRejectsEmptyBundles() {
        String json = "{\"format\":\"scripty-projects\",\"formatVersion\":1,\"projects\":[]}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "p.scripty.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        ScriptImportException e = assertThrows(ScriptImportException.class, () -> service.importProjects(file));
        assertTrue(e.getUserMessage().contains("doesn't contain any projects"));
    }

    @Test
    void importProjectRejectsNonScriptyFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.json", "application/json", "{\"foo\":1}".getBytes(StandardCharsets.UTF_8));
        ScriptImportException e = assertThrows(ScriptImportException.class, () -> service.importProjects(file));
        assertTrue(e.getUserMessage().contains("isn't a Scripty project file"));
    }

    @Test
    void importProjectRejectsNewerFormatVersions() {
        String json = "{\"format\":\"scripty-project\",\"formatVersion\":99}";
        MockMultipartFile file = new MockMultipartFile(
                "file", "p.scripty.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        ScriptImportException e = assertThrows(ScriptImportException.class, () -> service.importProjects(file));
        assertTrue(e.getUserMessage().contains("newer version"));
    }

    @Test
    void importProjectRoundTripRewiresReferences() throws Exception {
        String json = """
                {
                  "format": "scripty-project",
                  "formatVersion": 1,
                  "project": {
                    "title": "My Musical",
                    "screenplayTitle": "MY MUSICAL",
                    "writers": "Jane Doe"
                  },
                  "editions": [
                    { "key": 3, "name": "Original", "defaultEdition": true, "published": true }
                  ],
                  "characters": [
                    { "key": 11, "name": "ALICE", "fullName": "Alice Smith", "editionKey": 3 }
                  ],
                  "documents": [
                    { "key": 21, "title": "Opening Song", "documentType": "SONG", "content": "La la la", "sortOrder": 0 }
                  ],
                  "blocks": [
                    { "order": 5, "type": "SCENE", "content": "INT. HOUSE - DAY", "editionKey": 3 },
                    { "order": 9, "type": "CHARACTER", "content": "ALICE", "editionKey": 3,
                      "characterKey": 11, "sourceDocumentKey": 21 }
                  ]
                }
                """;

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(idSequence.incrementAndGet());
            }
            return p;
        });
        when(scriptEditionRepository.save(any(ScriptEdition.class))).thenAnswer(inv -> {
            ScriptEdition e = inv.getArgument(0);
            e.setId(idSequence.incrementAndGet());
            return e;
        });
        when(personRepository.save(any(Person.class))).thenAnswer(inv -> {
            Person p = inv.getArgument(0);
            p.setId(idSequence.incrementAndGet());
            return p;
        });
        when(textDocumentRepository.save(any(TextDocument.class))).thenAnswer(inv -> {
            TextDocument d = inv.getArgument(0);
            d.setId(idSequence.incrementAndGet());
            return d;
        });
        when(blockRepository.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "p.scripty.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
        Project imported = service.importProjects(file).get(0);

        assertEquals("My Musical", imported.getTitle());
        assertEquals("MY MUSICAL", imported.getScreenplayTitle());

        ArgumentCaptor<ScriptEdition> editionCaptor = ArgumentCaptor.forClass(ScriptEdition.class);
        verify(scriptEditionRepository).save(editionCaptor.capture());
        ScriptEdition edition = editionCaptor.getValue();
        assertTrue(edition.isDefault());
        assertTrue(edition.isPublished());
        assertEquals("Original", edition.getName());

        ArgumentCaptor<Person> personCaptor = ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(personCaptor.capture());
        Person person = personCaptor.getValue();
        assertEquals("ALICE", person.getName());
        assertEquals(edition.getId(), person.getScriptEdition().getId());

        ArgumentCaptor<TextDocument> documentCaptor = ArgumentCaptor.forClass(TextDocument.class);
        verify(textDocumentRepository).save(documentCaptor.capture());
        TextDocument document = documentCaptor.getValue();
        assertEquals("Opening Song", document.getTitle());

        ArgumentCaptor<Block> blockCaptor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository, org.mockito.Mockito.times(2)).save(blockCaptor.capture());
        List<Block> blocks = blockCaptor.getAllValues();
        // Orders are renumbered densely from 1 regardless of file values.
        assertEquals(1, blocks.get(0).getOrder());
        assertEquals(2, blocks.get(1).getOrder());
        assertEquals(Block.TYPE_SCENE, blocks.get(0).getType());
        assertNull(blocks.get(0).getPerson());
        assertEquals(person.getId(), blocks.get(1).getPerson().getId());
        assertEquals(document.getId(), blocks.get(1).getSourceDocumentId());
        assertEquals(edition.getId(), blocks.get(1).getScriptEdition().getId());

        verify(projectVersionService).autoSaveVersion(imported.getId(), edition.getId());
    }
}
