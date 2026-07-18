package com.scripty.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.scripty.api.ApiRel;
import com.scripty.dto.CapitalizationPreferencesPayload;
import com.scripty.service.CapitalizationPreferences;
import com.scripty.service.UserService;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.afford;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Editor preferences that must outlive a browser profile, unlike the
 * localStorage-only view toggles. Capitalization qualifies because exports
 * bake it in server-side.
 *
 * <p>Every endpoint here requires authentication: the security config rejects
 * anonymous callers before they reach a handler (pinned by
 * {@code ApiHypermediaIntegrationTest}), so {@code principal} is non-null.
 * Preferences are per-account, and there is no useful anonymous answer.
 */
@RestController
@RequestMapping(value = "/api/preferences")
public class UserPreferenceRestController {

    @Autowired
    private UserService userService;

    @RequestMapping(value = "/capitalization", method = RequestMethod.GET,
            produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    public ResponseEntity<EntityModel<Map<String, Boolean>>> readCapitalization(Principal principal) {
        return ResponseEntity.ok(capitalizationModel(
                userService.readCapitalizationPreferences(principal.getName())));
    }

    @RequestMapping(value = "/capitalization", method = RequestMethod.POST,
            produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    public ResponseEntity<EntityModel<Map<String, Boolean>>> updateCapitalization(
            @RequestBody CapitalizationPreferencesPayload body,
            Principal principal) {
        CapitalizationPreferences current = userService.readCapitalizationPreferences(principal.getName());
        // Partial updates: an absent key keeps its stored value, so the toggle
        // can post just the type the user clicked.
        CapitalizationPreferences next = new CapitalizationPreferences(
                flag(body == null ? null : body.getScene(), current.scene()),
                flag(body == null ? null : body.getCharacter(), current.character()),
                flag(body == null ? null : body.getTransition(), current.transition()),
                flag(body == null ? null : body.getShot(), current.shot()));
        return ResponseEntity.ok(capitalizationModel(
                userService.updateCapitalizationPreferences(principal.getName(), next)));
    }

    private EntityModel<Map<String, Boolean>> capitalizationModel(CapitalizationPreferences preferences) {
        return EntityModel.of(toMap(preferences))
                .add(capitalizationSelf().andAffordance(
                        afford(methodOn(UserPreferenceRestController.class).updateCapitalization(null, null))))
                .add(linkTo(methodOn(UserPreferenceRestController.class).updateCapitalization(null, null))
                        .withRel(ApiRel.UPDATE));
    }

    private static org.springframework.hateoas.Link capitalizationSelf() {
        return linkTo(methodOn(UserPreferenceRestController.class).readCapitalization(null)).withSelfRel();
    }

    private static boolean flag(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private static Map<String, Boolean> toMap(CapitalizationPreferences preferences) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("scene", preferences.scene());
        map.put("character", preferences.character());
        map.put("transition", preferences.transition());
        map.put("shot", preferences.shot());
        return map;
    }
}
