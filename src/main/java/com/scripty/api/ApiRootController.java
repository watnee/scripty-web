package com.scripty.api;

import com.scripty.controller.ActorRestController;
import com.scripty.controller.ProjectRestController;
import com.scripty.controller.TeamRestController;
import com.scripty.controller.TextDocumentRestController;
import com.scripty.controller.UserRestController;
import com.scripty.dto.TextDocument;
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

    @GetMapping(produces = MediaTypes.HAL_JSON_VALUE)
    public RepresentationModel<?> root() {
        RepresentationModel<?> root = new RepresentationModel<>();
        root.add(
                linkTo(methodOn(ApiRootController.class).root()).withSelfRel(),
                linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS),
                linkTo(methodOn(ProjectRestController.class).list(null)).withRel(ApiRel.PROJECTS),
                linkTo(methodOn(ActorRestController.class).list(null, null)).withRel(ApiRel.ACTORS),
                linkTo(methodOn(TeamRestController.class).list()).withRel(ApiRel.TEAMS),
                // Documents and songs are always scoped to one project, so unlike
                // the other root rels these come out templated: a null value for
                // the required projectId leaves {projectId} for the client to fill.
                linkTo(methodOn(TextDocumentRestController.class).list(null, null, null))
                        .withRel(ApiRel.DOCUMENTS),
                linkTo(methodOn(TextDocumentRestController.class).list(null, TextDocument.TYPE_SONG, null))
                        .withRel(ApiRel.SONGS)
        );
        return root;
    }
}
