ALTER TABLE project
    ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_project_deleted ON project (deleted_at);
