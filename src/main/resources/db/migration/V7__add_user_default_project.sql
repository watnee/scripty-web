ALTER TABLE `user` ADD COLUMN default_project_id INT NULL;
ALTER TABLE `user` ADD CONSTRAINT fk_user_default_project FOREIGN KEY (default_project_id) REFERENCES project(id) ON DELETE SET NULL;
