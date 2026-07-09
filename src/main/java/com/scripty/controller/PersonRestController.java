package com.scripty.controller;

import com.scripty.api.PersonResource;
import com.scripty.api.PersonResourceAssembler;
import com.scripty.api.RestErrors;
import com.scripty.commandmodel.person.createperson.CreatePersonCommandModel;
import com.scripty.commandmodel.person.editperson.EditPersonCommandModel;
import com.scripty.dto.Person;
import com.scripty.service.PersonService;
import com.scripty.viewmodel.person.personlist.PersonListViewModel;
import com.scripty.viewmodel.person.personprofile.PersonProfileViewModel;
import jakarta.validation.Valid;
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
@RequestMapping(value = "/api/character")
public class PersonRestController {

    @Autowired
    PersonService personService;

    @Autowired
    PersonResourceAssembler personResourceAssembler;

    @RequestMapping(method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<CollectionModel<EntityModel<PersonResource>>> list(@RequestParam Integer projectId) {
        PersonListViewModel viewModel = personService.getPersonListViewModel(projectId);
        return ResponseEntity.ok(
                personResourceAssembler.toCharacterCollection(viewModel.getCharacters(), projectId));
    }

    @RequestMapping(method = RequestMethod.POST, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> create(
            @Valid @RequestBody CreatePersonCommandModel commandModel, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        Person person = personService.saveCreatePersonCommandModel(commandModel);
        EntityModel<PersonResource> resource = personResourceAssembler.toModel(person);
        return ResponseEntity
                .created(resource.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(resource);
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<PersonResource>> show(@PathVariable Integer id) {
        PersonProfileViewModel viewModel = personService.getPersonProfileViewModel(id);
        return ResponseEntity.ok(personResourceAssembler.toModel(viewModel));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.PUT, consumes = "application/json", produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @Valid @RequestBody EditPersonCommandModel commandModel,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(RestErrors.from(bindingResult), HttpStatus.BAD_REQUEST);
        }
        commandModel.setId(id);
        Person person = personService.saveEditPersonCommandModel(commandModel);
        return ResponseEntity.ok(personResourceAssembler.toModel(person));
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE, produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<EntityModel<PersonResource>> delete(@PathVariable Integer id) {
        Person person = personService.deletePerson(id);
        return ResponseEntity.ok(personResourceAssembler.toDeleteModel(person));
    }
}
