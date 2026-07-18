package com.scripty.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.scripty.service.CapitalizationPreferences;
import com.scripty.service.UserService;

/**
 * Editor preferences that must outlive a browser profile, unlike the
 * localStorage-only view toggles. Capitalization qualifies because exports
 * bake it in server-side.
 */
@RestController
@RequestMapping(value = "/api/preferences")
public class UserPreferenceRestController {

    @Autowired
    private UserService userService;

    @RequestMapping(value = "/capitalization", method = RequestMethod.GET)
    public ResponseEntity<Map<String, Boolean>> readCapitalization(Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(toMap(CapitalizationPreferences.ALL));
        }
        return ResponseEntity.ok(toMap(userService.readCapitalizationPreferences(principal.getName())));
    }

    @RequestMapping(value = "/capitalization", method = RequestMethod.POST)
    public ResponseEntity<Map<String, Boolean>> updateCapitalization(
            @RequestBody Map<String, Boolean> body,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        CapitalizationPreferences current = userService.readCapitalizationPreferences(principal.getName());
        // Partial updates: an absent key keeps its stored value, so the toggle
        // can post just the type the user clicked.
        CapitalizationPreferences next = new CapitalizationPreferences(
                flag(body, "scene", current.scene()),
                flag(body, "character", current.character()),
                flag(body, "transition", current.transition()),
                flag(body, "shot", current.shot()));
        return ResponseEntity.ok(toMap(userService.updateCapitalizationPreferences(principal.getName(), next)));
    }

    private static boolean flag(Map<String, Boolean> body, String key, boolean fallback) {
        if (body == null) {
            return fallback;
        }
        Boolean value = body.get(key);
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
