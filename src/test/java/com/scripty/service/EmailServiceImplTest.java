package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scripty.observability.ScriptyMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EmailServiceImplTest {

    /** Real metrics against an in-memory registry — nothing to stub, nothing to assert on. */
    private static ScriptyMetrics metrics() {
        return new ScriptyMetrics(new SimpleMeterRegistry());
    }

    @Test
    void sendRecordsTheTransportItUsed() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", false, "", "", "", new ScriptyMetrics(registry));

        service.send("user@example.com", "Subject", "<p>Body</p>");

        // A deploy with no transport configured drops password resets silently: the
        // caller sees no exception and the HTTP request still succeeds. This counter
        // is the only signal that it happened, so assert it is actually recorded.
        assertEquals(1.0, registry.get("scripty.email.sent")
                .tag("transport", "disabled")
                .tag("outcome", "success")
                .counter().count());
    }

    @Test
    void sendRecordsAFailureWhenTheTransportThrows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        // A worker URL with a secret but an unroutable host: takes the Worker branch
        // and fails inside it, which is the path the email-failure alert watches.
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", false, "",
                "http://localhost:1/internal/email", "secret", new ScriptyMetrics(registry));

        assertThrows(IllegalStateException.class,
                () -> service.send("user@example.com", "Subject", "<p>Body</p>"));

        assertEquals(1.0, registry.get("scripty.email.sent")
                .tag("transport", "cloudflare_worker")
                .tag("outcome", "failure")
                .counter().count());
    }

    @Test
    void sendIsSkippedWhenNoTransportConfigured() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", false, "", "", "", metrics());
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void sendIsSkippedWhenMailEnabledButNoSmtpHostOrEmailWorker() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", true, "", "", "", metrics());
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void sendIsSkippedWhenEmailWorkerUrlSetWithoutSecret() {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@localhost>", false,
                "", "https://scripty.example.workers.dev/internal/email", "", metrics());
        assertDoesNotThrow(() -> service.send("user@example.com", "Subject", "<p>Body</p>"));
    }

    @Test
    void emailPayloadContainsFromToSubjectAndHtml() throws Exception {
        EmailServiceImpl service = new EmailServiceImpl(null,
                "Scripty <noreply@solfege.app>", false,
                "", "https://scripty.example.workers.dev/internal/email", "secret", metrics());

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
                "", "https://scripty.example.workers.dev/internal/email", "secret", metrics());
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
