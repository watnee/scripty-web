package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.dto.Person;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.ActorRepository;
import com.scripty.repository.PersonRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A newly created character must show up in the project's character list. */
class PersonServiceImplCreateEditionTest {

    private static final int PROJECT_ID = 5;
    private static final int EDITION_ID = 9;

    private final List<Person> stored = new ArrayList<>();

    private PersonRepository personRepository;
    private PersonServiceImpl service;

    private Project project;
    private ScriptEdition edition;

    @BeforeEach
    void setUp() {
        personRepository = mock(PersonRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        ScriptEditionService scriptEditionService = mock(ScriptEditionService.class);
        service = new PersonServiceImpl(
                personRepository,
                mock(ActorRepository.class),
                projectRepository,
                mock(ProjectActivityService.class),
                scriptEditionService);

        project = new Project();
        project.setId(PROJECT_ID);
        project.setTitle("Test Project");
        edition = new ScriptEdition();
        edition.setId(EDITION_ID);
        edition.setProject(project);

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scriptEditionService.getDefaultForProject(PROJECT_ID)).thenReturn(edition);
        when(personRepository.save(any(Person.class))).thenAnswer(i -> {
            Person p = i.getArgument(0);
            p.setId(stored.size() + 1);
            stored.add(p);
            return p;
        });
        // Listing filters by edition; only characters actually assigned to it come back.
        when(personRepository.findByScriptEditionIdOrderByNameAsc(EDITION_ID)).thenAnswer(i -> stored.stream()
                .filter(p -> p.getScriptEdition() != null && EDITION_ID == p.getScriptEdition().getId())
                .toList());
        when(personRepository.findByProjectIdOrderByNameAsc(PROJECT_ID)).thenAnswer(i -> List.copyOf(stored));
    }

    @Test
    void createdCharacterIsListedForProjectWithDefaultEdition() {
        CreatePersonCommandModel cmd = new CreatePersonCommandModel();
        cmd.setProjectId(PROJECT_ID);
        cmd.setName("JANE");
        cmd.setFullName("Jane Doe");

        Person saved = service.saveCreatePersonCommandModel(cmd);

        assertNotNull(saved.getScriptEdition(), "new character must be assigned the default edition");
        assertEquals(EDITION_ID, saved.getScriptEdition().getId());

        PersonListViewModel vm = service.getPersonListViewModel(PROJECT_ID);
        assertEquals(1, vm.getCharacters().size());
        assertEquals("JANE", vm.getCharacters().get(0).getName());
    }
}
