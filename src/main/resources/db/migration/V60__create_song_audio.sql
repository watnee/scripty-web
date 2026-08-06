-- Recordings kept with the song they belong to: the voice memo the chorus was
-- first sung into, the demo the band sent back, the reference track the writer
-- is chasing. Until now the words lived here and the sound of them lived in
-- someone's phone, and nothing connected the two.
--
-- Two tables rather than one, the shape actor_headshot already uses. The bytes
-- of an audio file are the largest thing this database holds and the least
-- often wanted: a song's list of recordings is drawn on every visit to the
-- editor, and every one of those reads would drag megabytes of PCM through
-- Hibernate if the blob sat in the row beside the name. So `song_audio` is the
-- metadata, mapped as an entity and cheap to list, and `song_audio_data` holds
-- what is actually played, read only when someone presses play.
--
-- In the database and not on disk, for the reason the headshots are: the app
-- deploys with no persistent volume, containers overlap during a release, and
-- a file written by the instance going away is a file the instance arriving
-- cannot find.
CREATE TABLE song_audio (
    id INT NOT NULL AUTO_INCREMENT,
    text_document_id INT NOT NULL,
    -- What the writer calls this take. Seeded from the file name, renameable
    -- afterwards, and never the file name itself once renamed — "Chorus idea,
    -- 2am" is the useful label and `voice-memo-4.m4a` is not.
    title VARCHAR(200) NOT NULL,
    -- The name it arrived under, kept so a download hands back something the
    -- writer recognises and so the extension survives a rename.
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    -- How long it plays, in milliseconds, as measured by whoever uploaded it —
    -- the browser's audio element or the phone's AVFoundation, both of which
    -- have already decoded the file to show a scrubber. Null when the uploader
    -- could not say. A list draws a duration when it has one and nothing when
    -- it does not, which is why this is not a required column.
    duration_ms INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (text_document_id) REFERENCES text_document(id) ON DELETE CASCADE,
    INDEX idx_song_audio_document (text_document_id, sort_order, id)
);

-- LONGBLOB, not MEDIUMBLOB: MEDIUMBLOB stops at 16 MB and a four-minute
-- lossless bounce is past that. The service caps an upload well below either
-- (app.song-audio-max-bytes), so the column is sized not to be the limit.
CREATE TABLE song_audio_data (
    song_audio_id INT NOT NULL,
    data LONGBLOB NOT NULL,
    PRIMARY KEY (song_audio_id),
    FOREIGN KEY (song_audio_id) REFERENCES song_audio(id) ON DELETE CASCADE
);
