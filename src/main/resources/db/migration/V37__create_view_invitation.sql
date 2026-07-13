CREATE TABLE view_invitation (
    id INT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    token VARCHAR(64) NOT NULL,
    project_id INT NOT NULL,
    invited_by_user_id INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    last_viewed_at DATETIME NULL,
    view_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_view_invitation_token (token),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (invited_by_user_id) REFERENCES `user`(id) ON DELETE SET NULL
);

CREATE INDEX idx_view_invitation_email ON view_invitation (email);
CREATE INDEX idx_view_invitation_project_status ON view_invitation (project_id, status);
