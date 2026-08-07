-- The song editor's undo/redo stacks, out of the HTTP session and into the
-- database — V44 did exactly this for the screenplay, and for the same reason.
--
-- API clients authenticate on every request and keep no session; the iOS client
-- refuses cookies outright, so each of its calls landed in a session of its own.
-- A stack held in session attributes was therefore rebuilt empty on the request
-- that asked whether there was anything to undo: undo-redo-status answered
-- canUndo:false forever, and the song editor's Undo button never came out of its
-- greyed state on any device. The browser, which does keep a session, never saw
-- it.
--
-- One row per (document, edition, writer): a collaborator's undo rewinds their
-- own edits, and an undo in one edition never applies a snapshot taken in
-- another. Both stacks are JSON arrays of encoded line snapshots, newest first,
-- capped at fifty entries by the service.
--
-- Unlike project_undo_state this needs no edition_key stand-in: every path into
-- the song service resolves a concrete edition first, so the column is NOT NULL
-- and the unique constraint can name it directly.
CREATE TABLE song_undo_state (
    id INT NOT NULL AUTO_INCREMENT,
    text_document_id INT NOT NULL,
    song_edition_id INT NOT NULL,
    user_id INT NOT NULL,
    undo_json LONGTEXT NOT NULL,
    redo_json LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_song_undo_state_document_edition_user UNIQUE (text_document_id, song_edition_id, user_id),
    FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE,
    FOREIGN KEY (song_edition_id) REFERENCES song_edition(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
);

CREATE INDEX idx_song_undo_state_document_id ON song_undo_state (text_document_id);
