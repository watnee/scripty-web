CREATE TABLE text_document (
    id INT NOT NULL AUTO_INCREMENT,
    project_id INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    document_type VARCHAR(30) NOT NULL DEFAULT 'SONG',
    content LONGTEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

CREATE INDEX idx_text_document_project_id ON text_document (project_id);
