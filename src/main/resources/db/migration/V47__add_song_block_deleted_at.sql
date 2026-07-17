ALTER TABLE song_block
    ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_song_block_document_deleted ON song_block (text_document_id, deleted_at);
