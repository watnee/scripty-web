CREATE TABLE project_activity (
    id INT NOT NULL AUTO_INCREMENT,
    project_id INT NOT NULL,
    actor_user_id INT NULL,
    action_type VARCHAR(50) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    entity_type VARCHAR(50) NULL,
    entity_id INT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (actor_user_id) REFERENCES `user`(id) ON DELETE SET NULL
);

CREATE INDEX idx_project_activity_project_created ON project_activity (project_id, created_at);
