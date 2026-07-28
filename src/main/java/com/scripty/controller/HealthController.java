package com.scripty.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint for Railway healthchecks. Returns 200 once the app is up.
 *
 * <p>The body also identifies which build is answering. Railway rolls a
 * deployment back to the previous image when the healthcheck never passes and
 * keeps serving it, so "the deploy pipeline went green" and "the new build is
 * live" are separate claims. CI compares {@code deploymentId} here against the
 * deployment it just created to tell them apart — see the
 * "Confirm the new deployment is live" step in {@code .github/workflows/ci-cd.yml}.
 *
 * <p>{@code RAILWAY_DEPLOYMENT_ID} is injected by Railway at runtime, so it is
 * absent locally and in tests; {@link BuildProperties} is absent until the
 * {@code build-info} goal has run (i.e. during {@code mvn test}). Both are
 * optional and simply omitted from the body.
 */
@RestController
public class HealthController {

    private final BuildProperties buildProperties;
    private final String deploymentId;

    public HealthController(ObjectProvider<BuildProperties> buildProperties,
            @Value("${RAILWAY_DEPLOYMENT_ID:}") String deploymentId) {
        this.buildProperties = buildProperties.getIfAvailable();
        this.deploymentId = deploymentId;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "ok");
        if (buildProperties != null) {
            body.put("version", buildProperties.getVersion());
            if (buildProperties.getTime() != null) {
                body.put("buildTime", buildProperties.getTime().toString());
            }
        }
        if (!deploymentId.isBlank()) {
            body.put("deploymentId", deploymentId);
        }
        return body;
    }
}
