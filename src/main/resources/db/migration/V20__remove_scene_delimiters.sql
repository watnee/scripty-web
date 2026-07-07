-- Scenes are plain SCENE-type blocks now; delimiter scenes (with the separate
-- name-edit header UI) are retired. Convert existing delimiter blocks so they
-- render as regular inline scene blocks.
UPDATE block SET scene_delimiter = FALSE WHERE scene_delimiter = TRUE;
