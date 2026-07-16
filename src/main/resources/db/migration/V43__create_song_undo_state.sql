CREATE TABLE song_undo_state (
    id INT NOT NULL AUTO_INCREMENT,
    text_document_id INT NOT NULL,
    user_id INT NOT NULL,
    undo_json LONGTEXT NOT NULL,
    redo_json LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_song_undo_state_document_user UNIQUE (text_document_id, user_id),
    FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
);

CREATE INDEX idx_song_undo_state_document_id ON song_undo_state (text_document_id);
