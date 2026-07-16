package com.scripty.controller;

import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ContactSuggestionService;
import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Name lookup behind the invite-by-email forms: typing a person's name offers
 * their address so the sender does not have to remember it. Scoped to one
 * project and to contacts the caller can already see, so it is not a directory
 * of every user's email.
 */
@RestController
@RequestMapping(value = "/api/project/{projectId}/contact-suggestions")
public class ContactSuggestionRestController {

    private final ContactSuggestionService contactSuggestionService;
    private final ProjectAccessSupport projectAccess;

    @Autowired
    public ContactSuggestionRestController(ContactSuggestionService contactSuggestionService,
                                           ProjectAccessSupport projectAccess) {
        this.contactSuggestionService = contactSuggestionService;
        this.projectAccess = projectAccess;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<List<ContactSuggestionViewModel>> suggest(@PathVariable Integer projectId,
                                                                    @RequestParam(name = "q", defaultValue = "") String query,
                                                                    Principal principal) {
        User currentUser = projectAccess.currentUser(principal);
        if (currentUser == null || !projectAccess.canAccessProject(projectId, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(contactSuggestionService.suggest(projectId, currentUser, query));
    }
}
