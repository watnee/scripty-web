ALTER TABLE `block`
    ADD COLUMN source_document_id INT NULL;

ALTER TABLE `block`
    ADD CONSTRAINT fk_block_source_document
        FOREIGN KEY (source_document_id) REFERENCES text_document(id) ON DELETE SET NULL;

CREATE INDEX idx_block_source_document_id ON `block` (source_document_id);
