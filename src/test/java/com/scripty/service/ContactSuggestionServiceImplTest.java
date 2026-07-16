package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.scripty.dto.Actor;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.repository.ActorRepository;
import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContactSuggestionServiceImplTest {

    private ActorRepository actorRepository;
    private UserService userService;
    private ProjectService projectService;
    private ContactSuggestionServiceImpl service;

    private Project project;
    private User currentUser;

    @BeforeEach
    void setUp() {
        actorRepository = mock(ActorRepository.class);
        userService = mock(UserService.class);
        projectService = mock(ProjectService.class);
        service = new ContactSuggestionServiceImpl(actorRepository, userService, projectService);

        project = new Project();
        project.setId(7);
        currentUser = user("director", "Dana", "Reed", "dana@example.com");

        when(projectService.readWithTeams(7)).thenReturn(project);
        when(projectService.canUserAccessProject(eq(project), any(User.class))).thenReturn(true);
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7)).thenReturn(List.of());
        when(userService.list()).thenReturn(List.of());
    }

    private User user(String username, String first, String last, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(email);
        return user;
    }

    private Actor actor(String first, String last, String email) {
        Actor actor = new Actor();
        actor.setFirstName(first);
        actor.setLastName(last);
        actor.setEmail(email);
        return actor;
    }

    @Test
    void suggestsCastMemberByPartialName() {
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(actor("Sarah", "Chen", "sarah@example.com")));

        List<ContactSuggestionViewModel> matches = service.suggest(7, currentUser, "sar");

        assertEquals(1, matches.size());
        assertEquals("Sarah Chen", matches.get(0).getName());
        assertEquals("sarah@example.com", matches.get(0).getEmail());
        assertEquals("Cast", matches.get(0).getSourceLabel());
    }

    @Test
    void matchesOnLastNameAndIgnoresCase() {
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(actor("Sarah", "Chen", "sarah@example.com")));

        assertEquals(1, service.suggest(7, currentUser, "CHEN").size());
    }

    @Test
    void skipsContactsWithoutASendableEmail() {
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(
                        actor("Nomail", "Nolan", null),
                        actor("Blank", "Nolan", "   "),
                        actor("Bogus", "Nolan", "not-an-address"),
                        actor("Spaced", "Nolan", "two words@example.com")));

        assertTrue(service.suggest(7, currentUser, "nolan").isEmpty());
    }

    @Test
    void everySuggestionCarriesAnAddress() {
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(actor("Sarah", "Chen", "sarah@example.com")));
        when(userService.list()).thenReturn(List.of(user("mia", "Mia", "Chen", "mia@example.com")));

        for (ContactSuggestionViewModel contact : service.suggest(7, currentUser, "chen")) {
            assertTrue(contact.getEmail().contains("@"), "expected an address, got " + contact.getEmail());
        }
    }

    @Test
    void excludesUsersWithoutProjectAccess() {
        User outsider = user("outsider", "Sam", "Outside", "sam@example.com");
        when(userService.list()).thenReturn(List.of(outsider));
        when(projectService.canUserAccessProject(project, outsider)).thenReturn(false);

        assertTrue(service.suggest(7, currentUser, "sam").isEmpty());
    }

    @Test
    void returnsNothingWhenCallerCannotAccessProject() {
        when(projectService.canUserAccessProject(project, currentUser)).thenReturn(false);
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(actor("Sarah", "Chen", "sarah@example.com")));

        assertTrue(service.suggest(7, currentUser, "sarah").isEmpty());
    }

    @Test
    void offersEachAddressOnceWhenCastMemberIsAlsoAUser() {
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(actor("Sarah", "Chen", "sarah@example.com")));
        when(userService.list()).thenReturn(List.of(user("sarah", "Sarah", "Chen", "SARAH@example.com")));

        List<ContactSuggestionViewModel> matches = service.suggest(7, currentUser, "sarah");

        assertEquals(1, matches.size());
        assertEquals("Cast", matches.get(0).getSourceLabel());
    }

    @Test
    void rankByPrefixMatchThenName() {
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7))
                .thenReturn(List.of(
                        actor("Zoe", "Andersen", "zoe@example.com"),
                        actor("Anna", "Bell", "anna@example.com")));

        List<ContactSuggestionViewModel> matches = service.suggest(7, currentUser, "an");

        assertEquals(List.of("Anna Bell", "Zoe Andersen"),
                matches.stream().map(ContactSuggestionViewModel::getName).toList());
    }

    @Test
    void capsTheNumberOfSuggestions() {
        List<Actor> crowd = new java.util.ArrayList<>();
        for (int i = 0; i < ContactSuggestionServiceImpl.MAX_SUGGESTIONS + 5; i++) {
            crowd.add(actor("Sam" + i, "Smith", "sam" + i + "@example.com"));
        }
        when(actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(7)).thenReturn(crowd);

        assertEquals(ContactSuggestionServiceImpl.MAX_SUGGESTIONS,
                service.suggest(7, currentUser, "smith").size());
    }

    @Test
    void returnsNothingForBlankQuery() {
        assertTrue(service.suggest(7, currentUser, "  ").isEmpty());
        assertTrue(service.suggest(7, currentUser, null).isEmpty());
    }
}
