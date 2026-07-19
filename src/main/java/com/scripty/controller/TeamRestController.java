package com.scripty.controller;

import com.scripty.api.AssignProductionsRequest;
import com.scripty.api.RestErrors;
import com.scripty.api.TeamResource;
import com.scripty.api.TeamResourceAssembler;
import com.scripty.commandmodel.team.TeamCommandModel;
import com.scripty.dto.Team;
import com.scripty.service.TeamService;
import jakarta.validation.Valid;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/team")
public class TeamRestController {

    @Autowired
    TeamService teamService;

    @Autowired
    TeamResourceAssembler teamResourceAssembler;

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<CollectionModel<EntityModel<TeamResource>>> list() {
        return ResponseEntity.ok(teamResourceAssembler.toTeamCollection(teamService.list()));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> create(
            @Valid @RequestBody TeamCommandModel commandModel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        try {
            Team team = teamService.create(commandModel.getName());
            EntityModel<TeamResource> resource = teamResourceAssembler.toModel(team);
            return ResponseEntity
                    .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("name", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<TeamResource>> show(@PathVariable Integer id) {
        Team team = teamService.read(id);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(teamResourceAssembler.toModel(team));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody TeamCommandModel commandModel,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        Team existing = teamService.read(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            teamService.update(id, commandModel.getName(), null);
            Team team = teamService.read(id);
            return ResponseEntity.ok(teamResourceAssembler.toModel(team));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("name", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Assigns the productions a team works on.
     *
     * <p>Separate from the rename above rather than folded into it:
     * {@code TeamService.update} takes both a name and a project list, and
     * passing null for either means "leave alone" — so a single endpoint would
     * make it impossible to clear a team's productions, since an empty list and
     * an omitted one would look the same by the time they reached the service.
     * A dedicated endpoint reads the empty list as "assign nothing".
     */
    @RequestMapping(value = "/{id}/productions", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<?> assignProductions(@PathVariable Integer id,
                                               @RequestBody AssignProductionsRequest request) {
        Team existing = teamService.read(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (request.projectIds() == null) {
            return new ResponseEntity<>(
                    Map.of("projectIds", "You must supply a list of productions, possibly empty."),
                    HttpStatus.BAD_REQUEST);
        }
        try {
            // Null name leaves it alone; the list is applied as given.
            teamService.update(id, null, request.projectIds());
            return ResponseEntity.ok(teamResourceAssembler.toModel(teamService.read(id)));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(Map.of("projectIds", e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE})
    public ResponseEntity<EntityModel<TeamResource>> delete(@PathVariable Integer id) {
        Team team = teamService.read(id);
        if (team == null) {
            return ResponseEntity.notFound().build();
        }
        teamService.delete(id);
        return ResponseEntity.ok(teamResourceAssembler.toDeleteModel(team));
    }
}
