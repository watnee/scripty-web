package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ScriptEditionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScriptEditionServiceImplTouchTest {

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
    void touchEditionReloadsByIdBeforeSaving() {
        // Simulate a detached lazy proxy: only id is trustworthy after
        // clearAutomatically bulk updates in create-below.
        ScriptEdition detached = new ScriptEdition();
        detached.setId(42);

        Project project = new Project();
        project.setId(9);

        ScriptEdition managed = new ScriptEdition();
        managed.setId(42);
        managed.setName("Main");
        managed.setProject(project);

        when(scriptEditionRepository.findById(42)).thenReturn(Optional.of(managed));
        when(scriptEditionRepository.save(any(ScriptEdition.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.touchEdition(detached);

        ArgumentCaptor<ScriptEdition> editionCaptor = ArgumentCaptor.forClass(ScriptEdition.class);
        verify(scriptEditionRepository).save(editionCaptor.capture());
        assertEquals(42, editionCaptor.getValue().getId());
        assertEquals("Main", editionCaptor.getValue().getName());

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        assertEquals(9, projectCaptor.getValue().getId());
    }

    @Test
    void touchEditionNoopsWhenEditionMissing() {
        ScriptEdition detached = new ScriptEdition();
        detached.setId(99);
        when(scriptEditionRepository.findById(99)).thenReturn(Optional.empty());

        service.touchEdition(detached);

        verify(scriptEditionRepository, never()).save(any());
        verify(projectRepository, never()).save(any());
    }
}
