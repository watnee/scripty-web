package com.scripty.api;

import com.scripty.controller.AccountRestController;
import com.scripty.controller.ActorRestController;
import com.scripty.controller.ProjectRestController;
import com.scripty.controller.TeamRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.controller.UserPreferenceRestController;
import com.scripty.controller.UserRestController;
import com.scripty.dto.TextDocument;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api")
public class ApiRootController {

    @Autowired
    ProjectAccessSupport projectAccess;

    @GetMapping(produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    public RepresentationModel<?> root(Principal principal) {
        RepresentationModel<?> root = new RepresentationModel<>();
        root.add(
                linkTo(methodOn(ApiRootController.class).root(null)).withSelfRel(),
                linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS),
                linkTo(methodOn(ActorRestController.class).list(null, null)).withRel(ApiRel.ACTORS),
                // Documents and songs are always scoped to one project, so unlike
                // the other root rels these come out templated: a null value for
                // the required projectId leaves {projectId} for the client to fill.
                linkTo(methodOn(TextDocumentRestController.class).list(null, null, null))
                        .withRel(ApiRel.DOCUMENTS),
                linkTo(methodOn(TextDocumentRestController.class).list(null, TextDocument.TYPE_SONG, null))
                        .withRel(ApiRel.SONGS),
                linkTo(methodOn(UserPreferenceRestController.class).readCapitalization(null))
                        .withRel(ApiRel.CAPITALIZATION_PREFERENCES),
                // Your own account: available to anyone signed in, unlike the
                // admin-only `users` below.
                linkTo(methodOn(AccountRestController.class).show(null)).withRel(ApiRel.ACCOUNT)
        );

        // Users and teams sit behind ROLE_ADMIN in the security config, so
        // advertising them to everyone hands most callers a link that can only
        // 403. A hypermedia client is entitled to treat an advertised rel as
        // something it may follow, so these are offered only to an admin.
        User user = projectAccess.currentUser(principal);
        if (user != null && user.isAdmin()) {
            root.add(linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS));
            root.add(linkTo(methodOn(TeamRestController.class).list()).withRel(ApiRel.TEAMS));
        }
        return root;
    }
}
