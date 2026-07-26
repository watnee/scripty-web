package com.scripty.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Apple App Site Association file. iOS validates a {@code webcredentials:}
 * associated domain by fetching this from the domain the app claims — it must
 * answer 200 as JSON, unauthenticated, with no redirect — and only then will
 * it offer this domain's passkeys to the native app.
 *
 * <p>The app ids are {@code <team id>.<bundle id>}. The defaults cover the two
 * bundle ids the install scripts produce: the project's own
 * {@code scripty.scripty} and the {@code com.<team>.scripty} fallback
 * install.sh writes when the default is taken.
 */
@RestController
public class WellKnownController {

    private final List<String> appIds;

    public WellKnownController(@Value("${app.apple-app-ids:"
            + "WA6FZ2S8R3.scripty.scripty,WA6FZ2S8R3.com.wa6fz2s8r3.scripty}") String appIds) {
        this.appIds = appIds == null || appIds.isBlank()
                ? List.of()
                : List.of(appIds.split("\\s*,\\s*"));
    }

    @GetMapping(value = "/.well-known/apple-app-site-association",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> appleAppSiteAssociation() {
        if (appIds.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("webcredentials", Map.of("apps", appIds)));
    }
}
