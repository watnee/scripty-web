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
                "Scripty <noreply@localhost>", false, "", "", "");
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void sendIsSkippedWhenMailEnabledButNoSmtpHostOrEmailWorker() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", true, "", "", "");
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void sendIsSkippedWhenEmailWorkerUrlSetWithoutSecret() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", false,
                "", "https://scripty.example.workers.dev/internal/email", "");
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void emailPayloadContainsFromToSubjectAndHtml() throws Exception {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@solfege.app>", false,
                "", "https://scripty.example.workers.dev/internal/email", "secret");

        String json = service.buildEmailPayload(
                "user@example.com", "Reset \"your\" password", "<p>Hi & welcome</p>");

        JsonNode payload = new ObjectMapper().readTree(json);
        assertEquals("Scripty <noreply@solfege.app>", payload.get("from").asText());
        assertEquals("user@example.com", payload.get("to").asText());
        assertEquals("Reset \"your\" password", payload.get("subject").asText());
        assertEquals("<p>Hi & welcome</p>", payload.get("html").asText());
    }
}
