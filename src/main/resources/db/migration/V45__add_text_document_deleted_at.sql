ALTER TABLE text_document
    ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_text_document_project_deleted ON text_document (project_id, deleted_at);
