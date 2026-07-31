-- Archiving a whole screenplay takes it out of the project list without deleting it.
--
-- The same second stamp V56 gave songs and notes, one level up. The trash is a
-- recovery window for a mistake, on a clock; the archive is a decision that a
-- production has wrapped. An archived project keeps its script, songs, notes,
-- editions and versions, stays openable by id, and is still included in a
-- .scripty.json bundle export — it is only hidden from the list.
--
-- A project can be archived and then trashed; deleted_at wins, so the archive
-- listing asks for archived_at IS NOT NULL, and the entity's @SQLRestriction on
-- deleted_at takes the trashed ones out for free.

ALTER TABLE project
    ADD COLUMN archived_at DATETIME NULL;

-- Serves both halves of the split: the project list (archived_at IS NULL) and
-- the archive listing (archived_at IS NOT NULL ORDER BY archived_at DESC).
CREATE INDEX idx_project_archived ON project (archived_at);
