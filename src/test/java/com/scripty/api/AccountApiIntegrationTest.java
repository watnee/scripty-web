package com.scripty.api;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The account surface is the signed-in user's own: their password and their
 * passkeys. That makes it different from every other new rel here — it is not
 * admin-gated, because there is no way to name someone else's account through
 * it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootAdvertisesTheAccountToAnyOrdinaryMember() throws Exception {
        // Unlike `users` and `teams`, which are withheld from non-admins.
        mockMvc.perform(get("/api").accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.['scripty:account'].href",
                        org.hamcrest.Matchers.containsString("/api/account")));
    }

    /**
     * The security config used to group {@code /api/account/**} with the
     * admin-only paths, which would have made "change my own password" an admin
     * privilege. This pins the rule that moved it out.
     *
     * <p>Asserted as "not forbidden" rather than "ok" deliberately: the test
     * profile seeds no users, so the handler answers 401 for this unknown
     * principal. A 403 would mean the security rule regressed, which is the
     * thing worth catching.
     */
    @Test
    void ownAccountIsNotAdminOnly() throws Exception {
        int status = mockMvc.perform(get("/api/account")
                        .accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, status, "a member must not be forbidden from their own account");

        int passkeyStatus = mockMvc.perform(get("/api/account/passkeys")
                        .accept(MediaTypes.HAL_JSON).with(user("member").roles("USER")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, passkeyStatus, "a member must not be forbidden from their own passkeys");
    }

    @Test
    void accountStaysBehindAuthentication() throws Exception {
        mockMvc.perform(get("/api/account").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/account/passkeys").accept(MediaTypes.HAL_JSON))
                .andExpect(status().isUnauthorized());
    }
}
