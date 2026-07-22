package com.scripty.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Password recovery over the API, which is the one flow whose caller is signed
 * out by definition.
 *
 * <p>That makes discovery the awkward part: every document that would advertise
 * the flow sits behind the sign-in. The 401 challenge is the one response such
 * a caller is guaranteed to see, so the link rides on that — and it is worth
 * pinning, because losing it would break nothing visible on the server and
 * would simply leave clients with no way to find recovery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordRecoveryApiTest {

    @Autowired
    MockMvc mockMvc;

    /**
     * The challenge still challenges — a native client needs the 401 to know it
     * must ask for credentials — and now also says where recovery lives. The
     * relation is bare rather than curied: this body is written by hand, outside
     * the HAL serializer that does the namespacing.
     */
    @Test
    void theChallengeCarriesTheWayIntoRecovery() throws Exception {
        mockMvc.perform(get("/api").accept("application/hal+json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$._links.forgotPassword.href").value(notNullValue()))
                .andExpect(jsonPath("$._links.projects").doesNotExist());
    }

    /**
     * Asking for a reset says the same thing either way. A different answer for
     * a registered address would make this a way of testing who has an account.
     */
    @Test
    void saysTheSameThingForAnyAddress() throws Exception {
        String unknown = requestFor("nobody@example.com");
        String maybeKnown = requestFor("admin@example.com");
        org.junit.jupiter.api.Assertions.assertEquals(unknown, maybeKnown,
                "the answer must not depend on whether the address is registered");
    }

    private String requestFor(String email) throws Exception {
        return mockMvc.perform(post("/api/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /**
     * A token that means nothing is a 200 saying so, not a 404 — a link sat in
     * an inbox for a week is an ordinary thing to click, and the caller needs
     * the reason to show. It arrives without the {@code resetPassword} link,
     * which is what stops a client offering the form anyway.
     */
    @Test
    void anUnknownTokenIsAnAnswerRatherThanAnError() throws Exception {
        mockMvc.perform(get("/api/forgot-password/reset")
                        .param("token", "not-a-real-token")
                        .accept("application/hal+json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$._links.['scripty:resetPassword']").doesNotExist());
    }

    @Test
    void resettingWithoutAPasswordIsRefused() throws Exception {
        mockMvc.perform(post("/api/forgot-password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept("application/hal+json")
                        .content("{\"token\":\"whatever\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.password").value(notNullValue()));
    }
}
