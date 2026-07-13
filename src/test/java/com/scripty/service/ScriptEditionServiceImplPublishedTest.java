package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.ProjectActivity;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ScriptEditionRepository;
import com.scripty.viewmodel.project.edition.ScriptEditionViewModel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScriptEditionServiceImplPublishedTest {

    @Mock
    private ScriptEditionRepository scriptEditionRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private ProjectActivityService projectActivityService;

    private ScriptEditionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScriptEditionServiceImpl(
                scriptEditionRepository,
                projectRepository,
                blockRepository,
                personRepository,
                projectActivityService);
    }

    @Test
    void resolveForAccessAllowsWritersAnyEdition() {
        ScriptEdition draft = edition(2, "Draft", false, false);
        when(scriptEditionRepository.findByIdAndProjectId(2, 1)).thenReturn(Optional.of(draft));

        ScriptEdition resolved = service.resolveForAccess(1, 2, true);

        assertEquals(2, resolved.getId());
        assertEquals("Draft", resolved.getName());
    }

    @Test
    void resolveForAccessLocksViewersToPublishedEdition() {
        ScriptEdition published = edition(3, "Blue", false, true);
        when(scriptEditionRepository.findPublishedByProjectId(1)).thenReturn(Optional.of(published));

        ScriptEdition resolved = service.resolveForAccess(1, 2, false);

        assertEquals(3, resolved.getId());
        assertEquals("Blue", resolved.getName());
    }

    @Test
    void resolveForAccessFallsBackToDefaultWhenNothingPublished() {
        ScriptEdition main = edition(1, "Main", true, false);
        when(scriptEditionRepository.findPublishedByProjectId(1)).thenReturn(Optional.empty());
        when(scriptEditionRepository.findDefaultByProjectId(1)).thenReturn(Optional.of(main));

        ScriptEdition resolved = service.resolveForAccess(1, 99, false);

        assertEquals(1, resolved.getId());
    }

    @Test
    void setPublishedEditionClearsOtherPublishedFlags() {
        ScriptEdition main = edition(1, "Main", true, true);
        ScriptEdition draft = edition(2, "Draft", false, false);
        when(scriptEditionRepository.findByIdAndProjectId(2, 1)).thenReturn(Optional.of(draft));
        when(scriptEditionRepository.findByProjectIdOrderByNameAsc(1)).thenReturn(List.of(main, draft));
        when(scriptEditionRepository.save(any(ScriptEdition.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.setPublishedEdition(2, 1));

        assertFalse(main.isPublished());
        assertTrue(draft.isPublished());
        verify(projectActivityService).recordForCurrentUser(
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(ProjectActivity.ACTION_SCRIPT_EDITED),
                org.mockito.ArgumentMatchers.contains("shared \"Draft\""),
                org.mockito.ArgumentMatchers.eq(ProjectActivity.ENTITY_PROJECT),
                org.mockito.ArgumentMatchers.eq(2));
    }

    @Test
    void getEditionViewModelsHidesUnpublishedFromViewers() {
        ScriptEdition main = edition(1, "Main", true, false);
        ScriptEdition blue = edition(2, "Blue", false, true);
        when(scriptEditionRepository.findByProjectIdOrderByNameAsc(1)).thenReturn(List.of(main, blue));
        when(scriptEditionRepository.findPublishedByProjectId(1)).thenReturn(Optional.of(blue));
        when(blockRepository.countByScriptEditionId(2)).thenReturn(4);

        List<ScriptEditionViewModel> editions = service.getEditionViewModels(1, false);

        assertEquals(1, editions.size());
        assertEquals(2, editions.get(0).getId());
        assertEquals("Blue", editions.get(0).getName());
        assertTrue(editions.get(0).isPublished());
    }

    @Test
    void deletePublishedEditionPromotesAnother() {
        ScriptEdition main = edition(1, "Main", true, false);
        ScriptEdition blue = edition(2, "Blue", false, true);
        when(scriptEditionRepository.findByIdAndProjectId(2, 1)).thenReturn(Optional.of(blue));
        when(scriptEditionRepository.countByProjectId(1)).thenReturn(2L);
        when(scriptEditionRepository.findByProjectIdOrderByNameAsc(1)).thenReturn(List.of(main));
        when(scriptEditionRepository.save(any(ScriptEdition.class))).thenAnswer(inv -> inv.getArgument(0));

        assertTrue(service.deleteEdition(2, 1));

        ArgumentCaptor<ScriptEdition> captor = ArgumentCaptor.forClass(ScriptEdition.class);
        verify(scriptEditionRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getId());
        assertTrue(captor.getValue().isPublished());
    }

    private static ScriptEdition edition(int id, String name, boolean isDefault, boolean isPublished) {
        Project project = new Project();
        project.setId(1);
        ScriptEdition edition = new ScriptEdition();
        edition.setId(id);
        edition.setName(name);
        edition.setDefault(isDefault);
        edition.setPublished(isPublished);
        edition.setProject(project);
        return edition;
    }
}
