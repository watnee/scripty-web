-- Recovery record for deleted script blocks.
--
-- Blocks are not soft-deleted in place: undo/redo and version restore wipe an
-- edition's block rows and re-insert them with fresh ids, so a deleted_at flag
-- on `block` would sit on a row that any undo can sweep away. Instead a delete
-- copies the block's content and formatting here, the same way a version
-- snapshot does, and a restore inserts a new block from this record.

CREATE TABLE deleted_block (
    id INT NOT NULL AUTO_INCREMENT,
    project_id INT NOT NULL,
    script_edition_id INT NULL,
    deleted_at DATETIME NOT NULL,
    deleted_by_user_id INT NULL,
    -- Position the block held when it was deleted. Only a hint: the script
    -- moves on, so restore clamps this to the edition's current length.
    original_order INT NOT NULL,
    content TEXT,
    `type` VARCHAR(50) NOT NULL,
    scene_delimiter BOOLEAN NOT NULL DEFAULT FALSE,
    bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    tags VARCHAR(255) NULL,
    text_align VARCHAR(20) NULL,
    font VARCHAR(50) NULL,
    highlight VARCHAR(20) NULL,
    text_bold BOOLEAN NOT NULL DEFAULT FALSE,
    text_italic BOOLEAN NOT NULL DEFAULT FALSE,
    text_underline BOOLEAN NOT NULL DEFAULT FALSE,
    -- The character cue is kept by name, not by id: the person row may be gone
    -- by the time this is restored, and cue blocks re-resolve names anyway.
    person_name VARCHAR(60) NULL,
    source_document_id INT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_deleted_block_project FOREIGN KEY (project_id)
        REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_deleted_block_script_edition FOREIGN KEY (script_edition_id)
        REFERENCES script_edition(id) ON DELETE CASCADE,
    CONSTRAINT fk_deleted_block_user FOREIGN KEY (deleted_by_user_id)
        REFERENCES `user`(id) ON DELETE SET NULL
);

CREATE INDEX idx_deleted_block_project_deleted ON deleted_block (project_id, deleted_at);

-- Drives the nightly purge sweep.
CREATE INDEX idx_deleted_block_deleted_at ON deleted_block (deleted_at);
