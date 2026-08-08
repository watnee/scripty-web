package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ProjectVersionRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectVersionServiceImplPruneTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectVersionRepository projectVersionRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private ActorRepository actorRepository;
    @Mock
    private ProjectActivityService projectActivityService;
    @Mock
    private ScriptEditionService scriptEditionService;

    private ProjectVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectVersionServiceImpl(
                projectRepository,
                projectVersionRepository,
                blockRepository,
                personRepository,
                actorRepository,
                new ObjectMapper(),
                projectActivityService,
                scriptEditionService);
    }

    @Test
    void pruneAutoSavesDoesNothingWhenEditionIdNull() {
        service.pruneAutoSaves(null);
        verify(projectVersionRepository, never()).findAutoSaveIdsByScriptEditionIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any());
        verify(projectVersionRepository, never()).deleteAllByIdIn(anyList());
    }

    @Test
    void pruneAutoSavesDoesNothingWhenAtOrUnderLimit() {
        Integer editionId = 7;
        when(projectVersionRepository.findAutoSaveIdsByScriptEditionIdOrderByCreatedAtDesc(editionId))
                .thenReturn(autoSaveIds(ProjectVersionServiceImpl.MAX_AUTO_SAVES_PER_EDITION));

        service.pruneAutoSaves(editionId);

        verify(projectVersionRepository, never()).deleteAllByIdIn(anyList());
    }

    @Test
    void pruneAutoSavesDeletesOldestBeyondLimit() {
        Integer editionId = 7;
        int total = ProjectVersionServiceImpl.MAX_AUTO_SAVES_PER_EDITION + 5;
        List<Integer> autoIds = autoSaveIds(total);
        when(projectVersionRepository.findAutoSaveIdsByScriptEditionIdOrderByCreatedAtDesc(editionId))
                .thenReturn(autoIds);

        service.pruneAutoSaves(editionId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectVersionRepository).deleteAllByIdIn(idsCaptor.capture());
        List<Integer> deleted = idsCaptor.getValue();
        assertEquals(5, deleted.size());
        // Newest first in list; overflow are the last 5 (oldest)
        assertEquals(autoIds.subList(ProjectVersionServiceImpl.MAX_AUTO_SAVES_PER_EDITION, total), deleted);
    }

    /** Auto-save ids newest first, the order the repository hands them back in. */
    private static List<Integer> autoSaveIds(int count) {
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(1000 + i);
        }
        return ids;
    }
}
