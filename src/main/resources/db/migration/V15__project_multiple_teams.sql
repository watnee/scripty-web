CREATE TABLE project_team (
    project_id int NOT NULL,
    team_id int NOT NULL,
    PRIMARY KEY (project_id, team_id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE
);

INSERT INTO project_team (project_id, team_id)
SELECT p.id, t.id
FROM project p
JOIN team t ON t.name = p.team
WHERE p.team IS NOT NULL AND p.team <> '';

CREATE INDEX idx_project_team_team_id ON project_team (team_id);

ALTER TABLE project DROP COLUMN team;
