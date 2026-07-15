ALTER TABLE `block`
    ADD COLUMN text_document_id INT NULL;

ALTER TABLE `block`
    ADD CONSTRAINT fk_block_text_document
        FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE;

CREATE INDEX idx_block_text_document_id ON `block` (text_document_id);
