CREATE TABLE actor_project (
    actor_id int NOT NULL,
    project_id int NOT NULL,
    PRIMARY KEY (actor_id, project_id),
    FOREIGN KEY (actor_id) REFERENCES actor(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

INSERT INTO actor_project (actor_id, project_id)
SELECT id, project_id FROM actor WHERE project_id IS NOT NULL;

INSERT INTO actor_project (actor_id, project_id)
SELECT DISTINCT p.actor_id, p.project_id
FROM person p
WHERE p.actor_id IS NOT NULL
AND NOT EXISTS (
    SELECT 1 FROM actor_project ap
    WHERE ap.actor_id = p.actor_id AND ap.project_id = p.project_id
);

CREATE INDEX idx_actor_project_project_id ON actor_project (project_id);

ALTER TABLE actor DROP CONSTRAINT fk_actor_project;

ALTER TABLE actor DROP COLUMN project_id;
