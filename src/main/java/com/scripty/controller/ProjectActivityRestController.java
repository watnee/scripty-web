package com.scripty.controller;

import com.scripty.api.ApiDates;
import com.scripty.api.ApiRel;
import com.scripty.api.ProjectActivityResource;
import com.scripty.dto.User;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ProjectActivityService;
import com.scripty.viewmodel.activity.ProjectActivityViewModel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * What has been happening to a screenplay — the production page's recent
 * activity, over REST.
 *
 * <p>Read-only by design. Entries are written by the services that perform the
 * actions, never by a client: an activity log a caller can post to is not a
 * record of what happened, it is a record of what someone said happened.
 */
@RestController
@RequestMapping("/api/project/{projectId}/activity")
public class ProjectActivityRestController {

    /** Enough to see the shape of recent work without paging. */
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    @Autowired
    ProjectActivityService projectActivityService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@PathVariable Integer projectId,
                                  @RequestParam(required = false) Integer limit,
                                  Principal principal) {
        if (!projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User user = projectAccess.currentUser(principal);
        int resolved = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<EntityModel<ProjectActivityResource>> resources = new ArrayList<>();
        List<ProjectActivityViewModel> entries =
                projectActivityService.listRecent(projectId, user, resolved);
        if (entries != null) {
            for (ProjectActivityViewModel entry : entries) {
                resources.add(EntityModel.of(toResource(entry)));
            }
        }

        return ResponseEntity.ok(CollectionModel.of(resources)
                .add(linkTo(methodOn(ProjectActivityRestController.class).list(projectId, null, null))
                        .withSelfRel())
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT)));
    }

    private ProjectActivityResource toResource(ProjectActivityViewModel entry) {
        ProjectActivityResource resource = new ProjectActivityResource();
        resource.setId(entry.getId());
        resource.setActorDisplayName(entry.getActorDisplayName());
        resource.setActionType(entry.getActionType());
        resource.setSummary(entry.getSummary());
        resource.setCreatedAt(ApiDates.toOffset(entry.getCreatedAt()));
        return resource;
    }
}
