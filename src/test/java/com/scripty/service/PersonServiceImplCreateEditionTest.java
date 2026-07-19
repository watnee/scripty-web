package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
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

/** Characters are listed by edition, so every write path must keep their edition set. */
class PersonServiceImplCreateEditionTest {

    private static final int PROJECT_ID = 5;
    private static final int EDITION_ID = 9;
    private static final int OTHER_EDITION_ID = 11;

    private final List<Person> stored = new ArrayList<>();

    private PersonRepository personRepository;
    private ScriptEditionService scriptEditionService;
    private PersonServiceImpl service;

    private Project project;
    private ScriptEdition edition;

    @BeforeEach
    void setUp() {
        personRepository = mock(PersonRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        scriptEditionService = mock(ScriptEditionService.class);
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
        // Mirrors requireForProject: an unknown or missing edition falls back to the default.
        when(scriptEditionService.requireForProject(eq(PROJECT_ID), any())).thenReturn(edition);
        when(personRepository.save(any(Person.class))).thenAnswer(i -> {
            Person p = i.getArgument(0);
            if (p.getId() == null) {
                p.setId(stored.size() + 1);
                stored.add(p);
            }
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

    @Test
    void editingAnEditionlessCharacterAssignsTheDefaultEdition() {
        // A character orphaned by an older code path: in the project, in no edition.
        Person orphan = new Person();
        orphan.setId(1);
        orphan.setName("JANE");
        orphan.setFullName("Jane Doe");
        orphan.setProject(project);
        stored.add(orphan);
        when(personRepository.findById(1)).thenReturn(Optional.of(orphan));

        assertEquals(0, service.getPersonListViewModel(PROJECT_ID).getCharacters().size());

        EditPersonCommandModel cmd = new EditPersonCommandModel();
        cmd.setId(1);
        cmd.setProjectId(PROJECT_ID);
        cmd.setName("JANE");
        cmd.setFullName("Jane Q. Doe");

        Person edited = service.saveEditPersonCommandModel(cmd);

        assertNotNull(edited.getScriptEdition(), "editing must heal a missing edition");
        assertEquals(EDITION_ID, edited.getScriptEdition().getId());
        assertEquals(1, service.getPersonListViewModel(PROJECT_ID).getCharacters().size());
    }

    @Test
    void editingKeepsANonDefaultEditionThatBelongsToTheProject() {
        // Healing must not drag characters out of a non-default edition they belong to.
        ScriptEdition other = new ScriptEdition();
        other.setId(OTHER_EDITION_ID);
        other.setProject(project);
        when(scriptEditionService.requireForProject(PROJECT_ID, OTHER_EDITION_ID)).thenReturn(other);

        Person person = new Person();
        person.setId(1);
        person.setName("JANE");
        person.setFullName("Jane Doe");
        person.setProject(project);
        person.setScriptEdition(other);
        stored.add(person);
        when(personRepository.findById(1)).thenReturn(Optional.of(person));

        EditPersonCommandModel cmd = new EditPersonCommandModel();
        cmd.setId(1);
        cmd.setProjectId(PROJECT_ID);
        cmd.setName("JANE");
        cmd.setFullName("Jane Q. Doe");

        Person edited = service.saveEditPersonCommandModel(cmd);

        assertEquals(OTHER_EDITION_ID, edited.getScriptEdition().getId());
    }
}
