CREATE TABLE song_block (
    id INT NOT NULL AUTO_INCREMENT,
    text_document_id INT NOT NULL,
    `order` INT NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE
);

CREATE INDEX idx_song_block_document_id ON song_block (text_document_id);
