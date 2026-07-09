package com.scripty.service;

import jakarta.mail.internet.MimeMessage;
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

    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final boolean mailEnabled;

    @Autowired
    public EmailServiceImpl(@Autowired(required = false) JavaMailSender mailSender,
                            @Value("${app.mail-from:Scripty <noreply@localhost>}") String mailFrom,
                            @Value("${app.mail-enabled:false}") boolean mailEnabled,
                            @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.mailEnabled = mailEnabled && mailSender != null && StringUtils.hasText(mailHost);
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        if (!mailEnabled) {
            // Never log the body — invitation emails contain one-time accept tokens.
            log.info("Mail disabled. Skipped email to={} subject={}", to, subject);
            return;
        }
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
