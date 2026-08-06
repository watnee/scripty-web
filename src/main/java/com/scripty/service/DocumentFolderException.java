package com.scripty.service;

/**
 * A folder operation refused for a reason worth saying out loud — a blank name,
 * or one already taken in this list.
 *
 * <p>Unchecked, and its message is written to be read by the person who typed
 * the name: both callers (the web form and the REST endpoint) put it straight
 * on screen. Everything else a folder call can fail for — an unknown project, a
 * folder from somewhere else, no permission — stays a null return, because
 * those are not things to explain to a writer.
 */
public class DocumentFolderException extends RuntimeException {

    public DocumentFolderException(String message) {
        super(message);
    }
}
