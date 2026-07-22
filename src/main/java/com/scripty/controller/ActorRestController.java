package com.scripty.controller;

import com.scripty.api.ActorResource;
import com.scripty.api.ActorResourceAssembler;
import com.scripty.api.RestErrors;
import com.scripty.api.SetAuditionsRequest;
import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.repository.ActorRepository;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ActorService;
import com.scripty.service.ActorHeadshotService;
import com.scripty.service.AuditionService;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
import com.scripty.viewmodel.actor.actorlist.ActorViewModel;
import com.scripty.viewmodel.actor.actorprofile.ActorProfileViewModel;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "/api/actor")
public class ActorRestController {

    @Autowired
    ActorService actorService;

    @Autowired
    ActorRepository actorRepository;

    @Autowired
    ActorResourceAssembler actorResourceAssembler;

    @Autowired
    AuditionService auditionService;

    @Autowired
    ActorHeadshotService actorHeadshotService;

    @Autowired
    ProjectAccessSupport projectAccess;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<ActorResource>>> list(
            @RequestParam(required = false) Integer projectId, Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (projectId != null) {
            ActorListViewModel viewModel = actorService.getActorListViewModel(projectId);
            return ResponseEntity.ok(
                    actorResourceAssembler.toActorCollection(viewModel.getActors(), projectId));
        }
        return ResponseEntity.ok(
                actorResourceAssembler.toActorCollectionFromEntities(actorRepository.findAllByOrderByFirstNameAsc()));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> create(
            @Valid @RequestBody CreateActorCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        Actor actor = actorService.saveCreateActorCommandModel(commandModel);
        EntityModel<ActorResource> resource = actorResourceAssembler.toModel(actor);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<ActorResource>> show(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ActorProfileViewModel viewModel = actorService.getActorProfileViewModel(id);
        return ResponseEntity.ok(actorResourceAssembler.toModel(viewModel));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody EditActorCommandModel commandModel,
            BindingResult bindingResult,
            Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        commandModel.setId(id);
        Actor actor = actorService.saveEditActorCommandModel(commandModel);
        return ResponseEntity.ok(actorResourceAssembler.toModel(actor));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<ActorResource>> delete(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Actor actor = actorService.deleteActor(id);
        return ResponseEntity.ok(actorResourceAssembler.toDeleteModel(actor));
    }

    /**
     * Stores a new headshot for this actor.
     *
     * Multipart rather than a raw image body, so the same
     * {@link com.scripty.service.ActorHeadshotService} the web form uses does
     * the validating — one place decides what an acceptable headshot is, and a
     * REST client cannot slip past a rule the browser is held to. Answers with
     * the refreshed actor, whose links now include {@code removeHeadshot}.
     */
    @RequestMapping(value = "/{id}/headshot", method = RequestMethod.POST, consumes = "multipart/form-data", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setHeadshot(
            @PathVariable Integer id,
            @RequestParam("headshot") MultipartFile headshot,
            Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Actor actor = actorRepository.findById(id).orElse(null);
        if (actor == null) {
            return ResponseEntity.notFound().build();
        }
        if (headshot == null || headshot.isEmpty()) {
            return new ResponseEntity<>(
                    RestErrors.of("headshot", "A headshot image is required."), HttpStatus.BAD_REQUEST);
        }
        try {
            actorHeadshotService.updateHeadshot(actor, headshot, false);
        } catch (IllegalArgumentException ex) {
            // Too big, or not an image we can serve back. The service's message
            // names the rule, so it is worth passing on rather than replacing.
            return new ResponseEntity<>(
                    RestErrors.of("headshot", ex.getMessage()), HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(actorResourceAssembler.toModel(actor));
    }

    /**
     * Takes the headshot away. Answers with the refreshed actor rather than an
     * empty body, so the caller sees the links it has left — the point being
     * that {@code removeHeadshot} is now gone from them.
     */
    @RequestMapping(value = "/{id}/headshot", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> removeHeadshot(@PathVariable Integer id, Principal principal) {
        if (!projectAccess.canViewCasting(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Actor actor = actorRepository.findById(id).orElse(null);
        if (actor == null) {
            return ResponseEntity.notFound().build();
        }
        actorHeadshotService.updateHeadshot(actor, null, true);
        return ResponseEntity.ok(actorResourceAssembler.toModel(actor));
    }

    /**
     * Replaces, wholesale, the set of characters this actor auditions for in the
     * given project. Answers with the refreshed project-scoped actor, so the
     * caller sees the audition ids it just set without a second read.
     */
    @RequestMapping(value = "/{id}/auditions", method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> setAuditions(
            @PathVariable Integer id,
            @RequestParam Integer projectId,
            @RequestBody SetAuditionsRequest request,
            Principal principal) {
        if (!projectAccess.canViewCasting(principal)
                || !projectAccess.canAccessProject(projectId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        auditionService.setAuditionsForActorInProject(
                id, projectId, request == null ? null : request.characterIds());

        ActorListViewModel viewModel = actorService.getActorListViewModel(projectId);
        ActorViewModel updated = viewModel.getActors().stream()
                .filter(actor -> actor.getId() == id)
                .findFirst()
                .orElse(null);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(actorResourceAssembler.toModel(updated, projectId));
    }
}
