package com.scripty.service;

public interface EmailService {

    default void send(String to, String subject, String htmlBody) {
        send(to, subject, htmlBody, null, null);
    }

    default void send(String to, String subject, String htmlBody, EmailAttachment attachment) {
        send(to, subject, htmlBody, null, attachment);
    }

    /**
     * Sends one message.
     *
     * <p>{@code textBody} is the plain-text alternative, not a second message: a
     * reader that cannot or will not render HTML sees it instead. Passing null
     * leaves the message HTML-only, which costs deliverability — a body with no
     * text part is a long-standing spam signal — so anything a person is meant
     * to act on should supply both.
     */
    void send(String to, String subject, String htmlBody, String textBody, EmailAttachment attachment);
}
