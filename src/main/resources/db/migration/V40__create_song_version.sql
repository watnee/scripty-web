CREATE TABLE song_version (
    id INT NOT NULL AUTO_INCREMENT,
    text_document_id INT NOT NULL,
    label VARCHAR(255) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE
);

CREATE INDEX idx_song_version_document_id ON song_version (text_document_id);
