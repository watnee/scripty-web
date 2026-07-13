ALTER TABLE script_edition ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE script_edition SET is_published = TRUE WHERE is_default = TRUE;
