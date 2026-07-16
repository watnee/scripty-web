package com.scripty.service;

import com.scripty.dto.Actor;
import com.scripty.dto.Project;
import com.scripty.dto.User;
import com.scripty.repository.ActorRepository;
import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactSuggestionServiceImpl implements ContactSuggestionService {

    static final int MAX_SUGGESTIONS = 8;

    private final ActorRepository actorRepository;
    private final UserService userService;
    private final ProjectService projectService;

    @Autowired
    public ContactSuggestionServiceImpl(ActorRepository actorRepository,
                                        UserService userService,
                                        ProjectService projectService) {
        this.actorRepository = actorRepository;
        this.userService = userService;
        this.projectService = projectService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactSuggestionViewModel> suggest(Integer projectId, User currentUser, String query) {
        String needle = normalize(query);
        if (projectId == null || currentUser == null || needle.isEmpty()) {
            return List.of();
        }
        Project project = projectService.readWithTeams(projectId);
        if (project == null || !projectService.canUserAccessProject(project, currentUser)) {
            return List.of();
        }

        // Keyed by email so the same person listed as both a cast member and a
        // user is offered once; the cast entry wins because it is added first.
        Map<String, ContactSuggestionViewModel> byEmail = new LinkedHashMap<>();
        for (Actor actor : actorRepository.findDistinctByProjects_IdOrderByFirstNameAsc(projectId)) {
            collect(byEmail, actorName(actor), actor.getEmail(), "Cast", needle);
        }
        for (User user : userService.list()) {
            if (!projectService.canUserAccessProject(project, user)) {
                continue;
            }
            collect(byEmail, userName(user), user.getEmail(), "Has access", needle);
        }

        List<ContactSuggestionViewModel> matches = new ArrayList<>(byEmail.values());
        matches.sort(Comparator
                .comparingInt((ContactSuggestionViewModel c) -> startsWith(c, needle) ? 0 : 1)
                .thenComparing(ContactSuggestionViewModel::getName, String.CASE_INSENSITIVE_ORDER));
        return matches.size() > MAX_SUGGESTIONS ? matches.subList(0, MAX_SUGGESTIONS) : matches;
    }

    private void collect(Map<String, ContactSuggestionViewModel> byEmail,
                         String name,
                         String email,
                         String sourceLabel,
                         String needle) {
        String cleanEmail = normalize(email);
        String cleanName = name == null ? "" : name.trim();
        // A contact with no usable address is never offered: picking one could
        // only ever put a name where an email address has to go.
        if (cleanName.isEmpty() || !isSendableEmail(cleanEmail) || !matches(cleanName, cleanEmail, needle)) {
            return;
        }
        if (byEmail.containsKey(cleanEmail)) {
            return;
        }
        ContactSuggestionViewModel vm = new ContactSuggestionViewModel();
        vm.setName(cleanName);
        vm.setEmail(cleanEmail);
        vm.setSourceLabel(sourceLabel);
        byEmail.put(cleanEmail, vm);
    }

    private boolean matches(String name, String email, String needle) {
        return name.toLowerCase(Locale.ROOT).contains(needle) || email.contains(needle);
    }

    private boolean startsWith(ContactSuggestionViewModel contact, String needle) {
        return contact.getName().toLowerCase(Locale.ROOT).startsWith(needle)
                || contact.getEmail().startsWith(needle);
    }

    /**
     * Deliberately narrow: an address is only offered when it is something the
     * invite forms would accept, so a suggestion can never be a bare name.
     */
    private boolean isSendableEmail(String email) {
        int at = email.indexOf('@');
        return at > 0
                && at == email.lastIndexOf('@')
                && at < email.length() - 1
                && email.indexOf(' ') < 0
                && email.indexOf('.', at) > at + 1
                && !email.endsWith(".");
    }

    private String actorName(Actor actor) {
        String first = actor.getFirstName() == null ? "" : actor.getFirstName().trim();
        String last = actor.getLastName() == null ? "" : actor.getLastName().trim();
        return (first + " " + last).trim();
    }

    private String userName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (first + " " + last).trim();
        return fullName.isEmpty() ? user.getUsername() : fullName;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
