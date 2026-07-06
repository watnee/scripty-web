-- Distinguish scene headings that open a new scene section from inline SCENE
-- element rows created by changing another block's type (those stay in place).

ALTER TABLE `block` ADD COLUMN scene_delimiter BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE `block` SET scene_delimiter = TRUE WHERE `type` = 'SCENE';
