package com.scripty.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression test for the "200 — something went wrong" error page shown to
 * anonymous visitors. Spring Security 6 defers CSRF token creation until the
 * token is first read; the nav fragment reads it after several KB of markup,
 * so once the page exceeded Tomcat's 8KB output buffer the response was
 * already committed and creating the token's HTTP session failed mid-render.
 *
 * <p>Runs against a real embedded Tomcat (not MockMvc) because the bug only
 * exists with real response buffering.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnonymousPageRenderingIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void loginPageRendersFullyForAnonymousVisitor() {
        ResponseEntity<String> response = restTemplate.getForEntity("/login", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("something went wrong");
        assertThat(response.getBody()).contains("name=\"_csrf\"");
        assertThat(response.getBody()).contains("</html>");
    }

    @Test
    void homePageRendersFullyForAnonymousVisitor() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("something went wrong");
        assertThat(response.getBody()).contains("name=\"_csrf\"");
        assertThat(response.getBody()).contains("</html>");
    }
}
