package com.scripty.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Apple App Site Association file. iOS validates an associated domain by
 * fetching this from the domain the app claims — it must answer 200 as JSON,
 * unauthenticated, with no redirect — and only then honours what the app asks
 * of that domain.
 *
 * <p>Two things are claimed here. {@code webcredentials} is what lets iOS offer
 * this domain's passkeys to the native app. {@code applinks} is what makes the
 * password recovery link a magic link: tapping it in Mail opens the app
 * straight at "choose a new password" rather than the browser, because the
 * token the app needs is already in the URL. An iPhone without the app
 * installed follows the same link to the web page, which is why the reset lives
 * at one URL rather than a separate scheme.
 *
 * <p>The app ids are {@code <team id>.<bundle id>}. The defaults cover the two
 * bundle ids the install scripts produce: the project's own
 * {@code scripty.scripty} and the {@code com.<team>.scripty} fallback
 * install.sh writes when the default is taken.
 */
@RestController
public class WellKnownController {

    /** The one path the app claims — the reset link from a recovery email. */
    static final String RESET_PATH = "/forgot-password/reset";

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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applinks", Map.of("details", List.of(resetLinkDetail())));
        body.put("webcredentials", Map.of("apps", appIds));
        // No cache headers set here on purpose: Spring Security already sends
        // `no-store, no-cache, must-revalidate` on every response, which is the
        // header we would want anyway — Apple's CDN sits between this and the
        // device, and telling it not to hold a copy is what keeps a deploy that
        // adds a claim from being ignored. Setting anything here is overwritten
        // by the header writer regardless.
        return ResponseEntity.ok(body);
    }

    /**
     * Claims the reset path, and only when it carries a token.
     *
     * <p>The narrowness matters: every other page on this domain — the marketing
     * pages, the web app itself, the reset page reached without a token — stays
     * with the browser, which is where a signed-in writer following a link from
     * a teammate expects to end up. A component that matched the whole site
     * would swallow all of it.
     */
    private Map<String, Object> resetLinkDetail() {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("/", RESET_PATH);
        // "?*" means "present, any value" — a reset page with no token in hand
        // has nothing to hand the app, so it is left to the browser.
        component.put("?", Map.of("token", "?*"));
        component.put("comment", "Open a password reset link in the Scripty app.");
        return Map.of("appIDs", appIds, "components", List.of(component));
    }
}
