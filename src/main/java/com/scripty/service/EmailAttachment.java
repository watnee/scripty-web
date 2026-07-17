package com.scripty.service;

/**
 * A file attached to an outgoing email. {@code content} holds the raw bytes;
 * the transport encodes them as needed (base64 for the Cloudflare Worker,
 * a MIME part for SMTP).
 */
public record EmailAttachment(String filename, String contentType, byte[] content) {
}
