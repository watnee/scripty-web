package com.scripty.controller;

import com.scripty.api.ActorResource;
import com.scripty.api.ActorResourceAssembler;
import com.scripty.api.RestErrors;
import com.scripty.commandmodel.actor.createactor.CreateActorCommandModel;
import com.scripty.commandmodel.actor.editactor.EditActorCommandModel;
import com.scripty.dto.Actor;
import com.scripty.repository.ActorRepository;
import com.scripty.security.ProjectAccessSupport;
import com.scripty.service.ActorService;
import com.scripty.viewmodel.actor.actorlist.ActorListViewModel;
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
}
