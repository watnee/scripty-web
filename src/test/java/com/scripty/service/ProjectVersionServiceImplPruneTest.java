package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.dto.ProjectVersion;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.repository.ProjectVersionRepository;
import java.time.LocalDateTime;
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
        verify(projectVersionRepository, never()).findAutoSavesByScriptEditionIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any());
        verify(projectVersionRepository, never()).deleteAllById(anyList());
    }

    @Test
    void pruneAutoSavesDoesNothingWhenAtOrUnderLimit() {
        Integer editionId = 7;
        when(projectVersionRepository.findAutoSavesByScriptEditionIdOrderByCreatedAtDesc(editionId))
                .thenReturn(autoSaves(editionId, ProjectVersionServiceImpl.MAX_AUTO_SAVES_PER_EDITION));

        service.pruneAutoSaves(editionId);

        verify(projectVersionRepository, never()).deleteAllById(anyList());
    }

    @Test
    void pruneAutoSavesDeletesOldestBeyondLimit() {
        Integer editionId = 7;
        int total = ProjectVersionServiceImpl.MAX_AUTO_SAVES_PER_EDITION + 5;
        List<ProjectVersion> autos = autoSaves(editionId, total);
        when(projectVersionRepository.findAutoSavesByScriptEditionIdOrderByCreatedAtDesc(editionId))
                .thenReturn(autos);

        service.pruneAutoSaves(editionId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectVersionRepository).deleteAllById(idsCaptor.capture());
        List<Integer> deleted = idsCaptor.getValue();
        assertEquals(5, deleted.size());
        // Newest first in list; overflow are the last 5 (oldest)
        assertEquals(
                autos.subList(ProjectVersionServiceImpl.MAX_AUTO_SAVES_PER_EDITION, total).stream()
                        .map(ProjectVersion::getId)
                        .toList(),
                deleted);
    }

    private static List<ProjectVersion> autoSaves(Integer editionId, int count) {
        ScriptEdition edition = new ScriptEdition();
        edition.setId(editionId);
        List<ProjectVersion> list = new ArrayList<>(count);
        LocalDateTime base = LocalDateTime.of(2026, 7, 1, 12, 0);
        for (int i = 0; i < count; i++) {
            ProjectVersion v = new ProjectVersion();
            v.setId(1000 + i);
            v.setScriptEdition(edition);
            v.setLabel("Auto-save Jul 1, 12:00 PM");
            // Descending createdAt (newest first), matching repository order
            v.setCreatedAt(base.minusMinutes(i));
            v.setSnapshotJson("{}");
            list.add(v);
        }
        return list;
    }
}
