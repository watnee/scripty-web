CREATE TABLE project_undo_state (
    id INT NOT NULL AUTO_INCREMENT,
    project_id INT NOT NULL,
    edition_id INT NULL,
    edition_key INT NOT NULL,
    user_id INT NOT NULL,
    undo_json LONGTEXT NOT NULL,
    redo_json LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_project_undo_state_project_edition_user UNIQUE (project_id, edition_key, user_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (edition_id) REFERENCES script_edition(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES `user`(id) ON DELETE CASCADE
);

CREATE INDEX idx_project_undo_state_project_id ON project_undo_state (project_id);
