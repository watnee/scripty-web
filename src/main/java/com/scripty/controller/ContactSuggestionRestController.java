package com.scripty.controller;

import com.scripty.api.ApiRel;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ContactSuggestionService;
import com.scripty.viewmodel.contact.ContactSuggestionViewModel;
import java.security.Principal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

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

    // application/json stays negotiable for the autofill script; with
    // spring.hateoas.use-hal-as-default-json-media-type it renders as HAL too.
    @RequestMapping(method = RequestMethod.GET,
            produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE,
                    MediaType.APPLICATION_JSON_VALUE })
    public ResponseEntity<CollectionModel<ContactSuggestionViewModel>> suggest(
            @PathVariable Integer projectId,
            @RequestParam(name = "q", defaultValue = "") String query,
            Principal principal) {
        User currentUser = projectAccess.currentUser(principal);
        if (currentUser == null || !projectAccess.canAccessProject(projectId, currentUser)) {
            return ResponseEntity.status(403).build();
        }
        List<ContactSuggestionViewModel> suggestions =
                contactSuggestionService.suggest(projectId, currentUser, query);
        return ResponseEntity.ok(CollectionModel.of(suggestions)
                // Self echoes the query that produced these results; the null
                // query variant stays templated so a client can re-search
                // without knowing this controller's parameter name.
                .add(linkTo(methodOn(ContactSuggestionRestController.class)
                        .suggest(projectId, query, null)).withSelfRel())
                .add(linkTo(methodOn(ContactSuggestionRestController.class)
                        .suggest(projectId, null, null)).withRel(ApiRel.CONTACT_SUGGESTIONS))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT)));
    }
}
