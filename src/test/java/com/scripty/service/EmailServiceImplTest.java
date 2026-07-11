package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EmailServiceImplTest {

    @Test
    void sendIsSkippedWhenNoTransportConfigured() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", false, "", "");
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void sendIsSkippedWhenMailEnabledButNoSmtpHostOrResendKey() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", true, "", "");
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void resendPayloadContainsFromToSubjectAndHtml() throws Exception {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <onboarding@resend.dev>", false, "", "re_test_key");

        String json = service.buildResendPayload(
                "user@example.com", "Reset \"your\" password", "<p>Hi & welcome</p>");

        JsonNode payload = new ObjectMapper().readTree(json);
        assertEquals("Scripty <onboarding@resend.dev>", payload.get("from").asText());
        assertEquals(1, payload.get("to").size());
        assertEquals("user@example.com", payload.get("to").get(0).asText());
        assertEquals("Reset \"your\" password", payload.get("subject").asText());
        assertEquals("<p>Hi & welcome</p>", payload.get("html").asText());
    }
}
