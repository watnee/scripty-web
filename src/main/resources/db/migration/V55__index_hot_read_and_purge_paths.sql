-- Indexes for three queries that currently have none they can use end to end.
--
-- Each one below names the repository method it serves. Nothing here changes
-- data or schema shape, so the migration is safe to replay against an empty
-- database (what the schema-check job does) and cheap against production.

-- BlockRepository.findByProjectIdOrderByOrderAscIdAsc — the editor's main read,
-- run on every script load. project_id is indexed only as a side effect of its
-- foreign key, so MySQL can find the project's rows but then has to filesort
-- them; a feature-length script is thousands of blocks sorted on every open.
-- Leading with project_id and carrying `order`, id in the index makes the sort
-- disappear. Also covers findByProjectIdAndOrder and findMaxOrderByProjectId.
--
-- The script_edition side of the same table already has this shape
-- (idx_block_script_edition_order, V30); this gives the project side parity.
CREATE INDEX idx_block_project_order ON `block` (project_id, `order`, id);

-- SongBlockRepository.findByDeletedAtNotNullAndDeletedAtBefore — the nightly
-- trash purge. idx_song_block_document_deleted (V47) leads with
-- text_document_id, so a sweep that only filters on deleted_at cannot use it
-- and scans the whole table.
CREATE INDEX idx_song_block_deleted_at ON song_block (deleted_at);

-- TextDocumentRepository.findByDeletedAtBefore — same nightly purge shape, same
-- reason: idx_text_document_project_deleted (V45) leads with project_id.
--
-- deleted_block already carries the equivalent index for its own purge
-- (idx_deleted_block_deleted_at, V49); these two tables were the ones missed.
CREATE INDEX idx_text_document_deleted_at ON text_document (deleted_at);
