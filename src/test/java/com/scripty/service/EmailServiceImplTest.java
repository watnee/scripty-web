package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
                "user@example.com", "Reset \"your\" password", "<p>Hi & welcome</p>", null);

        JsonNode payload = new ObjectMapper().readTree(json);
        assertEquals("Scripty <noreply@solfege.app>", payload.get("from").asText());
        assertEquals("user@example.com", payload.get("to").asText());
        assertEquals("Reset \"your\" password", payload.get("subject").asText());
        assertEquals("<p>Hi & welcome</p>", payload.get("html").asText());
        assertFalse(payload.has("attachments"));
    }

    @Test
    void emailPayloadEncodesAttachmentAsBase64() throws Exception {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@solfege.app>", false,
                "", "https://scripty.example.workers.dev/internal/email", "secret");
        byte[] bytes = "PDF-BYTES".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        EmailAttachment attachment = new EmailAttachment("script.pdf", "application/pdf", bytes);

        String json = service.buildEmailPayload(
                "user@example.com", "Screenplay", "<p>Attached</p>", attachment);

        JsonNode payload = new ObjectMapper().readTree(json);
        JsonNode entry = payload.get("attachments").get(0);
        assertEquals("script.pdf", entry.get("filename").asText());
        assertEquals("application/pdf", entry.get("type").asText());
        assertEquals("base64", entry.get("encoding").asText());
        assertEquals(java.util.Base64.getEncoder().encodeToString(bytes),
                entry.get("content").asText());
    }
}
