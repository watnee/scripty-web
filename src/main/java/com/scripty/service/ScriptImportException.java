package com.scripty.service;

import java.io.IOException;

/**
 * Import failure with a user-facing message (password-protected PDF, empty extract, etc.).
 */
public class ScriptImportException extends IOException {

    private final String userMessage;

    public ScriptImportException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public ScriptImportException(String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
