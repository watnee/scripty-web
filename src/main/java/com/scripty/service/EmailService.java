package com.scripty.service;

public interface EmailService {

    default void send(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, null);
    }

    void send(String to, String subject, String htmlBody, EmailAttachment attachment);
}
