ALTER TABLE text_document
    ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_text_document_deleted_at ON text_document (deleted_at);
