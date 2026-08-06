-- Folders for a project's songs and notes.
--
-- The list has only ever been one flat run of cards in the order the writer
-- dragged them into. That holds for the eight songs of a musical and stops
-- holding for the sixty a writer keeps in one project: an act, a set of cuts, a
-- pile of fragments and a finished record all sitting in the same column, told
-- apart only by their titles.
--
-- A folder is a name to put some of them under. Deliberately the smallest thing
-- that answers that:
--
--   * Flat. A folder holds documents, never other folders. Nesting would need
--     breadcrumbs, a move-into-tree picker and a cycle check, and it buys
--     nothing until a project has more folders than a screen holds.
--   * Per list, not per project. Songs and notes are two lists on the web and
--     two lists in the app, so document_type is part of what a folder is —
--     "Act One" under Songs and "Act One" under Notes are two folders, and
--     neither list is ever shown a folder belonging to the other.
--   * Optional. A document with no folder is not in some implicit root: it is
--     simply unfiled, and every list still shows it. Nothing about this
--     migration changes what an existing project looks like.
--
-- Deleting a folder does not delete what is in it — ON DELETE SET NULL below,
-- and the service says the same thing in words. Losing a night's lyrics to a
-- misplaced tap on a folder's menu is not a trade anyone would take, and the
-- trash already exists for deleting documents on purpose.

CREATE TABLE text_document_folder (
    id INT NOT NULL AUTO_INCREMENT,
    project_id INT NOT NULL,
    -- SONG or NOTES: which list this folder belongs to. Not nullable — a
    -- folder that belonged to both lists would have to appear in both, and
    -- then a document moved between lists would drag its folder with it.
    document_type VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    -- Two folders of one list cannot share a name. The list shows a folder by
    -- its name and nothing else, so a duplicate would be two headings a writer
    -- has no way to tell apart. Scoped by type, so "Act One" can exist under
    -- Songs and under Notes.
    UNIQUE KEY uk_text_document_folder_name (project_id, document_type, name),
    INDEX idx_text_document_folder_project (project_id, document_type)
);

-- Which folder a document is in, or NULL for the unfiled ones — which is every
-- document that exists today.
ALTER TABLE text_document
    ADD COLUMN folder_id INT NULL;

-- SET NULL rather than CASCADE: deleting a folder unfiles its documents, it
-- does not delete them. The service enforces the same rule for its own reasons
-- (it has to renumber and record activity), and this is the backstop for
-- anything that reaches the table another way.
ALTER TABLE text_document
    ADD CONSTRAINT fk_text_document_folder
    FOREIGN KEY (folder_id) REFERENCES text_document_folder(id) ON DELETE SET NULL;

-- Serves the grouping the list does: a project's documents, gathered by folder.
CREATE INDEX idx_text_document_folder_id ON text_document (folder_id);
