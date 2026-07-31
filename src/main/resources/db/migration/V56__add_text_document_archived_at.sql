-- Archiving a song or note takes it out of the working list without deleting it.
--
-- This is deliberately a separate stamp from deleted_at rather than another use
-- of the trash: the trash is a recovery window for a mistake, while the archive
-- is a decision to put finished work aside. An archived document keeps its
-- lyrics, versions and script insertions, stays openable by id, and is still
-- included in a whole-project bundle export — it is only hidden from the list.
--
-- A document can be archived and then trashed; deleted_at wins, so the archive
-- listing asks for archived_at IS NOT NULL AND deleted_at IS NULL.

ALTER TABLE text_document
    ADD COLUMN archived_at DATETIME NULL;

-- Serves TextDocumentRepository's list finder (…DeletedAtIsNullAndArchivedAtIsNull…)
-- and the archive listing (…ArchivedAtIsNotNullAndDeletedAtIsNull…), both of
-- which filter on project_id first.
CREATE INDEX idx_text_document_project_archived ON text_document (project_id, archived_at);
