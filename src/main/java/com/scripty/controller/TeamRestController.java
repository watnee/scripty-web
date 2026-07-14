package com.scripty.controller;

import com.scripty.api.ApiError;
import com.scripty.api.RestErrors;
import com.scripty.api.TeamResource;
import com.scripty.api.TeamResourceAssembler;
import com.scripty.commandmodel.team.TeamCommandModel;
import com.scripty.dto.Team;
import com.scripty.service.TeamService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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

    @RequestMapping(method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(teamResourceAssembler.toTeamCollection(teamService.list()));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
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
            return new ResponseEntity<>(ApiError.validation(Map.of("name", e.getMessage())), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> show(@PathVariable Integer id) {
        Team team = teamService.read(id);
        if (team == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.notFound());
        }
        return ResponseEntity.ok(teamResourceAssembler.toModel(team));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody TeamCommandModel commandModel,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        Team existing = teamService.read(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.notFound());
        }
        try {
            teamService.update(id, commandModel.getName(), null);
            Team team = teamService.read(id);
            return ResponseEntity.ok(teamResourceAssembler.toModel(team));
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(ApiError.validation(Map.of("name", e.getMessage())), HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = {MediaTypes.HAL_JSON_VALUE, "application/json"})
    public ResponseEntity<?> delete(@PathVariable Integer id) {
        Team team = teamService.read(id);
        if (team == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.notFound());
        }
        teamService.delete(id);
        return ResponseEntity.ok(teamResourceAssembler.toDeleteModel(team));
    }
}
