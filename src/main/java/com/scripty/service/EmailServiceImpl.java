package com.scripty.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String emailWorkerUrl;
    private final String emailWorkerSecret;
    private final boolean smtpEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    public EmailServiceImpl(@Autowired(required = false) JavaMailSender mailSender,
                            @Value("${app.mail-from:Scripty <noreply@localhost>}") String mailFrom,
                            @Value("${app.mail-enabled:false}") boolean mailEnabled,
                            @Value("${spring.mail.host:}") String mailHost,
                            @Value("${app.email-worker-url:}") String emailWorkerUrl,
                            @Value("${app.email-worker-secret:}") String emailWorkerSecret) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.emailWorkerUrl = emailWorkerUrl == null ? "" : emailWorkerUrl.trim();
        this.emailWorkerSecret = emailWorkerSecret == null ? "" : emailWorkerSecret.trim();
        this.smtpEnabled = mailEnabled && mailSender != null && StringUtils.hasText(mailHost);
    }

    @Override
    public void send(String to, String subject, String htmlBody, EmailAttachment attachment) {
        // The Cloudflare email Worker is an explicit opt-in: it takes precedence
        // over SMTP because Railway restricts outbound SMTP ports.
        if (StringUtils.hasText(emailWorkerUrl) && StringUtils.hasText(emailWorkerSecret)) {
            sendViaEmailWorker(to, subject, htmlBody, attachment);
            return;
        }
        if (!smtpEnabled) {
            // Never log the body — invitation emails contain one-time accept tokens.
            log.info("Mail disabled. Skipped email to={} subject={}", to, subject);
            return;
        }
        sendViaSmtp(to, subject, htmlBody, attachment);
    }

    private void sendViaEmailWorker(String to, String subject, String htmlBody,
                                    EmailAttachment attachment) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(emailWorkerUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + emailWorkerSecret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildEmailPayload(to, subject, htmlBody, attachment)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Email Worker returned status "
                        + response.statusCode() + " for email to " + to
                        + ": " + response.body());
            }
            log.info("Sent email via Cloudflare to={} subject={}", to, subject);
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending email to " + to, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }

    String buildEmailPayload(String to, String subject, String htmlBody, EmailAttachment attachment) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("from", mailFrom);
            payload.put("to", to);
            payload.put("subject", subject);
            payload.put("html", htmlBody);
            if (attachment != null && attachment.content() != null && attachment.content().length > 0) {
                // The Worker decodes base64 content back into raw bytes before
                // handing it to the Cloudflare Email Sending binding.
                ObjectNode entry = payload.putArray("attachments").addObject();
                entry.put("filename", attachment.filename());
                entry.put("type", attachment.contentType());
                entry.put("encoding", "base64");
                entry.put("content", Base64.getEncoder().encodeToString(attachment.content()));
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build email payload for " + to, e);
        }
    }

    private void sendViaSmtp(String to, String subject, String htmlBody, EmailAttachment attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (attachment != null && attachment.content() != null && attachment.content().length > 0) {
                helper.addAttachment(attachment.filename(),
                        new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }
}
