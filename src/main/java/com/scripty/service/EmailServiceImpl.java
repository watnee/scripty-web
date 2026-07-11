package com.scripty.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String resendApiKey;
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
                            @Value("${app.resend-api-key:}") String resendApiKey) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.resendApiKey = resendApiKey == null ? "" : resendApiKey.trim();
        this.smtpEnabled = mailEnabled && mailSender != null && StringUtils.hasText(mailHost);
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        // Setting a Resend API key is an explicit opt-in: it takes precedence over
        // SMTP because Railway restricts outbound SMTP ports.
        if (StringUtils.hasText(resendApiKey)) {
            sendViaResend(to, subject, htmlBody);
            return;
        }
        if (!smtpEnabled) {
            // Never log the body — invitation emails contain one-time accept tokens.
            log.info("Mail disabled. Skipped email to={} subject={}", to, subject);
            return;
        }
        sendViaSmtp(to, subject, htmlBody);
    }

    private void sendViaResend(String to, String subject, String htmlBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildResendPayload(to, subject, htmlBody)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Resend API returned status "
                        + response.statusCode() + " for email to " + to
                        + ": " + response.body());
            }
            log.info("Sent email via Resend to={} subject={}", to, subject);
        } catch (IllegalStateException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending email to " + to, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }

    String buildResendPayload(String to, String subject, String htmlBody) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("from", mailFrom);
            payload.putArray("to").add(to);
            payload.put("subject", subject);
            payload.put("html", htmlBody);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build email payload for " + to, e);
        }
    }

    private void sendViaSmtp(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }
}
