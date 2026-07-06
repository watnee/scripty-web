ALTER TABLE actor ADD COLUMN project_id int NULL;

ALTER TABLE actor ADD CONSTRAINT fk_actor_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE;

UPDATE actor a
SET project_id = (
    SELECT MIN(p.project_id)
    FROM person p
    WHERE p.actor_id = a.id
)
WHERE EXISTS (
    SELECT 1 FROM person p WHERE p.actor_id = a.id
);

CREATE INDEX idx_actor_project_id ON actor (project_id);
