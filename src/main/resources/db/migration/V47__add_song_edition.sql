-- Named, switchable "versions" for songs, mirroring script_edition (V30/V31).
-- A song is a text_document; its lyric blocks (song_block) and snapshots
-- (song_version) become scoped to a song_edition. Every existing song gets a
-- 'Main' edition that is both default and published.
CREATE TABLE song_edition (
    id INT NOT NULL AUTO_INCREMENT,
    text_document_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    cloned_from_edition_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited TIMESTAMP NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE,
    FOREIGN KEY (cloned_from_edition_id) REFERENCES song_edition(id) ON DELETE SET NULL,
    UNIQUE KEY uk_song_edition_document_name (text_document_id, name),
    INDEX idx_song_edition_document (text_document_id)
);

-- Seed a 'Main' edition (default AND published) for every song document.
INSERT INTO song_edition (text_document_id, name, is_default, is_published, created_at, updated_at, last_edited)
SELECT id, 'Main', TRUE, TRUE, COALESCE(updated_at, CURRENT_TIMESTAMP), COALESCE(updated_at, CURRENT_TIMESTAMP), updated_at
FROM text_document
WHERE document_type = 'SONG';

ALTER TABLE song_block ADD COLUMN song_edition_id INT NULL;
ALTER TABLE song_version ADD COLUMN song_edition_id INT NULL;

UPDATE song_block sb
SET song_edition_id = (
    SELECT se.id FROM song_edition se
    WHERE se.text_document_id = sb.text_document_id AND se.is_default = TRUE LIMIT 1
);

UPDATE song_version sv
SET song_edition_id = (
    SELECT se.id FROM song_edition se
    WHERE se.text_document_id = sv.text_document_id AND se.is_default = TRUE LIMIT 1
);

ALTER TABLE song_block ADD CONSTRAINT fk_song_block_song_edition
    FOREIGN KEY (song_edition_id) REFERENCES song_edition(id) ON DELETE CASCADE;
ALTER TABLE song_version ADD CONSTRAINT fk_song_version_song_edition
    FOREIGN KEY (song_edition_id) REFERENCES song_edition(id) ON DELETE CASCADE;

CREATE INDEX idx_song_block_song_edition_order ON song_block (song_edition_id, `order`);
CREATE INDEX idx_song_version_song_edition ON song_version (song_edition_id);
