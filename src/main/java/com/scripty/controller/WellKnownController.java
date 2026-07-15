package com.scripty.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the Apple App Site Association file that lets the native iOS client
 * share passkeys with this domain (the WebAuthn relying party). Apple fetches
 * {@code https://<host>/.well-known/apple-app-site-association} and checks that
 * the {@code webcredentials} service lists the app's {@code <TeamID>.<bundleId>}.
 */
@RestController
public class WellKnownController {

    private final String appleAppId;

    public WellKnownController(@Value("${app.apple-app-id:}") String appleAppId) {
        this.appleAppId = appleAppId;
    }

    @GetMapping(value = "/.well-known/apple-app-site-association",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> appleAppSiteAssociation() {
        if (appleAppId == null || appleAppId.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = Map.of(
                "webcredentials", Map.of("apps", List.of(appleAppId)));
        return ResponseEntity.ok(body);
    }
}
