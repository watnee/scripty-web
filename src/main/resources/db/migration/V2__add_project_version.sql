CREATE TABLE IF NOT EXISTS project_version (
    id int NOT NULL AUTO_INCREMENT,
    project_id int NOT NULL,
    label varchar(255) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    INDEX idx_project_version_project (project_id)
);
