-- A durable name for a song or note, so the same one can live in two places.
--
-- The id identifies a row in this database. That is enough while a song exists
-- only here, and stops being enough the moment the writer has a copy on a
-- signed-out device: that workspace numbers its documents from 1 as well, and
-- neither side can adopt the other's number. Without something that outlives
-- the storage, every crossing between the two has to treat the song as new —
-- which is how a lyric ends up in an account three times, each with its own
-- versions and none of them the one being written in.
--
-- So: a uid, minted when the document is created (TextDocument.assignUid) and
-- carried in the .scripty.json archive. A file coming back into a project it
-- was exported from can then say which songs it already has, and those keep
-- their id, their lines, their versions and their place.
--
-- Unique per project, not globally. It is only ever matched against the
-- documents of the project being written into, and a global constraint would
-- refuse a file exported from one account and imported into another — which is
-- an ordinary thing to do and describes two genuinely separate songs.

ALTER TABLE text_document
    ADD COLUMN uid VARCHAR(64) NULL;

-- Rows written before this column existed. `legacy-<id>` rather than a random
-- uuid because it needs no database-specific function (this migration runs
-- against MySQL in production and H2 in the tests), it is unique by
-- construction, and it can never collide with a uuid minted by an app — so an
-- old document and a new one are never mistaken for each other.
UPDATE text_document SET uid = CONCAT('legacy-', id) WHERE uid IS NULL;

-- Serves the lookup a replace-from-archive does: the documents of one project,
-- by uid.
CREATE UNIQUE INDEX idx_text_document_project_uid ON text_document (project_id, uid);
