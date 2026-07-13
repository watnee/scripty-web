CREATE TABLE script_edition (
    id INT NOT NULL AUTO_INCREMENT,
    project_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    cloned_from_edition_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited TIMESTAMP NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    FOREIGN KEY (cloned_from_edition_id) REFERENCES script_edition(id) ON DELETE SET NULL,
    UNIQUE KEY uk_script_edition_project_name (project_id, name),
    INDEX idx_script_edition_project (project_id)
);

INSERT INTO script_edition (project_id, name, is_default, created_at, updated_at, last_edited)
SELECT id, 'Main', TRUE, COALESCE(last_edited, CURRENT_TIMESTAMP), COALESCE(last_edited, CURRENT_TIMESTAMP), last_edited
FROM project;

ALTER TABLE `block` ADD COLUMN script_edition_id INT NULL;
ALTER TABLE person ADD COLUMN script_edition_id INT NULL;
ALTER TABLE project_version ADD COLUMN script_edition_id INT NULL;

UPDATE `block` b
SET script_edition_id = (
    SELECT se.id FROM script_edition se WHERE se.project_id = b.project_id AND se.is_default = TRUE LIMIT 1
);

UPDATE person p
SET script_edition_id = (
    SELECT se.id FROM script_edition se WHERE se.project_id = p.project_id AND se.is_default = TRUE LIMIT 1
);

UPDATE project_version pv
SET script_edition_id = (
    SELECT se.id FROM script_edition se WHERE se.project_id = pv.project_id AND se.is_default = TRUE LIMIT 1
);

ALTER TABLE `block` ADD CONSTRAINT fk_block_script_edition
    FOREIGN KEY (script_edition_id) REFERENCES script_edition(id) ON DELETE CASCADE;
ALTER TABLE person ADD CONSTRAINT fk_person_script_edition
    FOREIGN KEY (script_edition_id) REFERENCES script_edition(id) ON DELETE CASCADE;
ALTER TABLE project_version ADD CONSTRAINT fk_project_version_script_edition
    FOREIGN KEY (script_edition_id) REFERENCES script_edition(id) ON DELETE CASCADE;

CREATE INDEX idx_block_script_edition_order ON `block` (script_edition_id, `order`);
CREATE INDEX idx_person_script_edition ON person (script_edition_id);
CREATE INDEX idx_project_version_script_edition ON project_version (script_edition_id);
