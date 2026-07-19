package com.scripty.controller;

import com.scripty.api.ApiRel;
import com.scripty.api.InvitationResource;
import com.scripty.api.SendInvitationRequest;
import com.scripty.commandmodel.invitation.SendInvitationCommandModel;
import com.scripty.commandmodel.invitation.SendViewInvitationCommandModel;
import com.scripty.config.FeatureFlag;
import com.scripty.config.FeatureFlags;
import com.scripty.dto.User;
import com.scripty.security.InvitationRateLimiter;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.InvitationService;
import com.scripty.service.ViewInvitationService;
import com.scripty.viewmodel.invitation.PendingInvitationViewModel;
import com.scripty.viewmodel.invitation.ViewInvitationViewModel;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Managing who has been invited to a screenplay: collaborators who will get an
 * account, and view-only readers who will not.
 *
 * <p><strong>What this deliberately does not do.</strong> There is no endpoint
 * here to accept an invitation, and none to read a screenplay by view token.
 * Both exist already as browser routes, reached from the link in the email, and
 * both are the parts that must be usable by someone with no account and no
 * session. Adding REST twins would put an unauthenticated, account-creating
 * write path and a bearer-token read path into {@code /api} to serve a client
 * that never needs either: the person accepting an invitation is, by
 * definition, not yet using the app. So this controller manages invitations —
 * list, send, revoke — and the invitee's journey stays where it works.
 *
 * <p>Nothing here returns a token or an invite URL. The existing view models do
 * not carry them and this does not add them: an API that hands out a
 * long-lived read-anything-in-this-screenplay credential is a different and
 * much larger promise than one that says who was invited.
 *
 * <p>Sending is rate limited and gated behind {@link FeatureFlag#API_INVITATIONS},
 * which is off by default. A web form is paced by a person; an endpoint is
 * paced by whatever is calling it.
 */
@RestController
@RequestMapping("/api/project/{projectId}/invitations")
public class InvitationRestController {

    @Autowired
    InvitationService invitationService;

    @Autowired
    ViewInvitationService viewInvitationService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @Autowired
    InvitationRateLimiter rateLimiter;

    @Autowired
    FeatureFlags featureFlags;

    private boolean disabled() {
        return !featureFlags.isEnabled(FeatureFlag.API_INVITATIONS);
    }

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> list(@PathVariable Integer projectId, Principal principal) {
        if (disabled()) {
            return ResponseEntity.notFound().build();
        }
        // Seeing who has access is a management view, not a reader's view.
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    /**
     * Invites someone. A {@code viewOnly} request creates a read-only reader
     * rather than a collaborator.
     *
     * <p>Answers the same way whether or not the address already has an
     * account. The service returns null in that case on purpose, so as not to
     * reveal who is registered; returning a distinct status here — a 409 is the
     * obvious instinct — would undo that from the outside.
     */
    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> send(@PathVariable Integer projectId,
                                  @RequestBody SendInvitationRequest request,
                                  Principal principal) {
        if (disabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request.email() == null || request.email().isBlank()) {
            return new ResponseEntity<>(
                    Map.of("email", "You must supply an email address."), HttpStatus.BAD_REQUEST);
        }

        User inviter = projectAccess.currentUser(principal);
        if (inviter == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!rateLimiter.tryAcquire(inviter.getId())) {
            return new ResponseEntity<>(
                    Map.of("email", "Too many invitations sent. Try again later."),
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        try {
            if (request.viewOnlyOrFalse()) {
                SendViewInvitationCommandModel command = new SendViewInvitationCommandModel();
                command.setProjectId(projectId);
                command.setEmail(request.email());
                // Left off, and not exposed on the request. Attaching a PDF
                // renders the whole screenplay per invitation, which turns one
                // call into real CPU and a large outbound attachment. The web
                // form offers it because a person is choosing each time.
                command.setAttachPdf(false);
                viewInvitationService.sendInvitation(command, inviter);
            } else {
                SendInvitationCommandModel command = new SendInvitationCommandModel();
                command.setProjectId(projectId);
                command.setEmail(request.email());
                command.setTeamId(request.teamId());
                // A null result means the address already has an account. That
                // is a success from here: see the note above.
                invitationService.sendInvitation(command, inviter);
            }
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("email", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> revoke(@PathVariable Integer projectId,
                                    @PathVariable Integer id,
                                    Principal principal) {
        if (disabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!projectAccess.canEditScript(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User current = projectAccess.currentUser(principal);
        // Both services scope the revoke to the project, so an id from another
        // screenplay cannot be reached by guessing.
        try {
            invitationService.revoke(id, projectId, current);
        } catch (RuntimeException collaboratorMiss) {
            try {
                viewInvitationService.revoke(id, projectId, current);
            } catch (RuntimeException readerMiss) {
                return ResponseEntity.notFound().build();
            }
        }
        return ResponseEntity.ok(collection(projectId, principal));
    }

    private CollectionModel<EntityModel<InvitationResource>> collection(
            Integer projectId, Principal principal) {
        User current = projectAccess.currentUser(principal);
        List<EntityModel<InvitationResource>> resources = new ArrayList<>();

        List<PendingInvitationViewModel> pending =
                invitationService.getPendingInvitationsForProject(projectId, current);
        if (pending != null) {
            for (PendingInvitationViewModel invitation : pending) {
                InvitationResource resource = new InvitationResource();
                resource.setId(invitation.getId());
                resource.setEmail(invitation.getEmail());
                resource.setTeamName(invitation.getTeamName());
                resource.setStatusLabel(invitation.getStatusLabel());
                resource.setViewOnly(false);
                resources.add(EntityModel.of(resource, itemLinks(projectId, invitation.getId())));
            }
        }

        List<ViewInvitationViewModel> readers =
                viewInvitationService.getActiveInvitationsForProject(projectId, current);
        if (readers != null) {
            for (ViewInvitationViewModel invitation : readers) {
                InvitationResource resource = new InvitationResource();
                resource.setId(invitation.getId());
                resource.setEmail(invitation.getEmail());
                resource.setStatusLabel(invitation.getStatusLabel());
                resource.setViewOnly(true);
                resources.add(EntityModel.of(resource, itemLinks(projectId, invitation.getId())));
            }
        }

        return CollectionModel.of(resources)
                .add(linkTo(methodOn(InvitationRestController.class).list(projectId, null)).withSelfRel())
                .add(linkTo(methodOn(InvitationRestController.class).send(projectId, null, null))
                        .withRel(ApiRel.SEND_INVITATION))
                .add(linkTo(methodOn(ProjectRestController.class).show(projectId, null))
                        .withRel(ApiRel.PROJECT));
    }

    private Link[] itemLinks(Integer projectId, Integer id) {
        return new Link[]{
                linkTo(methodOn(InvitationRestController.class).revoke(projectId, id, null))
                        .withRel(ApiRel.REVOKE),
                linkTo(methodOn(InvitationRestController.class).list(projectId, null))
                        .withRel(ApiRel.INVITATIONS)
        };
    }
}
