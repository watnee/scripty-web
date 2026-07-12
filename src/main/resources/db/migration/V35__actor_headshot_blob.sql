-- Move actor headshots from the filesystem (Railway volume) into the database
-- so the web service needs no persistent volume and deploys can overlap
-- (zero downtime). Accessed via JdbcTemplate like the passkey tables, not JPA.

CREATE TABLE actor_headshot (
	actor_id int NOT NULL,
	content_type varchar(64) NOT NULL,
	data mediumblob NOT NULL,
	PRIMARY KEY (actor_id),
	CONSTRAINT fk_actor_headshot_actor FOREIGN KEY (actor_id) REFERENCES actor(id) ON DELETE CASCADE
);

-- Any existing headshot_path points at a file on the old volume, which is
-- empty in production — the paths are dangling. Null them so headshot_path is
-- set if and only if an actor_headshot row exists.
UPDATE actor SET headshot_path = NULL;
