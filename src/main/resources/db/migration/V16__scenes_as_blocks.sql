-- Replace the scene table with scene-type blocks: every block now belongs
-- directly to a project with a single flat order, and a block with
-- type = 'SCENE' acts as the scene heading (its content is the scene name).

CREATE TABLE block_new (
	id int NOT NULL AUTO_INCREMENT,
	`order` int NOT NULL,
	content TEXT NOT NULL,
	bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
	pinned BOOLEAN NOT NULL DEFAULT FALSE,
	tags VARCHAR(1024) NULL,
	`type` varchar(20) NOT NULL DEFAULT 'TEXT',
	person_id int NULL,
	project_id int NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE SET NULL,
	FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

INSERT INTO block_new (`order`, content, bookmarked, pinned, tags, `type`, person_id, project_id)
SELECT ROW_NUMBER() OVER (PARTITION BY u.project_id ORDER BY u.sort_key),
       u.content, u.bookmarked, u.pinned, u.tags, u.block_type, u.person_id, u.project_id
FROM (
	SELECT s.project_id AS project_id,
	       s.`order` * 100000 AS sort_key,
	       s.`name` AS content,
	       FALSE AS bookmarked,
	       FALSE AS pinned,
	       CAST(NULL AS VARCHAR(1024)) AS tags,
	       'SCENE' AS block_type,
	       CAST(NULL AS INT) AS person_id
	FROM scene s
	UNION ALL
	SELECT s.project_id,
	       s.`order` * 100000 + b.`order`,
	       b.content,
	       b.bookmarked,
	       b.pinned,
	       b.tags,
	       'TEXT',
	       b.person_id
	FROM `block` b
	JOIN scene s ON b.scene_id = s.id
) u;

DROP TABLE `block`;
DROP TABLE scene;

ALTER TABLE block_new RENAME TO `block`;
