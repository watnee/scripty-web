-- Deploy hardening: accounts created or seeded with default credentials must
-- rotate their password before using the app.
ALTER TABLE `user` ADD COLUMN password_change_required tinyint(1) NOT NULL DEFAULT 0;
