package com.scripty.api;

import com.scripty.controller.UserRestController;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.MediaTypes;
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
                linkTo(methodOn(UserRestController.class).list()).withRel(ApiRel.USERS)
        );
        return root;
    }
}
