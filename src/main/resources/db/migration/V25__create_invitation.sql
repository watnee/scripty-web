ALTER TABLE `user`
    ADD COLUMN email VARCHAR(100) NULL;

CREATE UNIQUE INDEX uk_user_email ON `user` (email);

CREATE TABLE invitation (
    id INT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    token VARCHAR(64) NOT NULL,
    team_id INT NOT NULL,
    project_id INT NULL,
    invited_by_user_id INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    accepted_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitation_token (token),
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE SET NULL,
    FOREIGN KEY (invited_by_user_id) REFERENCES `user`(id) ON DELETE SET NULL
);

CREATE INDEX idx_invitation_email ON invitation (email);
CREATE INDEX idx_invitation_team_status ON invitation (team_id, status);
CREATE INDEX idx_invitation_project_status ON invitation (project_id, status);
