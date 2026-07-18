-- Per-user control over which screenplay element types are auto-capitalized.
-- Defaults are 1 so existing users keep the current always-uppercase behavior.
ALTER TABLE `user` ADD COLUMN auto_caps_scene tinyint(1) NOT NULL DEFAULT 1;
ALTER TABLE `user` ADD COLUMN auto_caps_character tinyint(1) NOT NULL DEFAULT 1;
ALTER TABLE `user` ADD COLUMN auto_caps_transition tinyint(1) NOT NULL DEFAULT 1;
ALTER TABLE `user` ADD COLUMN auto_caps_shot tinyint(1) NOT NULL DEFAULT 1;
