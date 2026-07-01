CREATE TABLE IF NOT EXISTS `user` (
	id int NOT NULL AUTO_INCREMENT,
	username varchar(20) NOT NULL,
	`password` varchar(100) NOT NULL,
	enabled tinyint(1) NOT NULL,
	first_name varchar(30) NOT NULL,
	last_name varchar(30) NOT NULL,
	team varchar(50) NULL,
	PRIMARY KEY (id),
	INDEX idx_user_username (username)
);

CREATE TABLE IF NOT EXISTS authority (
	username varchar(20) NOT NULL,
	authority varchar(20) NOT NULL,
	PRIMARY KEY (username, authority),
	INDEX idx_authority_username (username)
);

CREATE TABLE IF NOT EXISTS project (
	id int NOT NULL AUTO_INCREMENT,
	title varchar(100) NOT NULL,
	team varchar(50) NULL,
	PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS scene (
	id int NOT NULL AUTO_INCREMENT,
	`order` int NOT NULL,
	`name` varchar(255) NOT NULL,
	project_id int NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS actor (
	id int NOT NULL AUTO_INCREMENT,
	first_name varchar(30) NOT NULL,
	last_name varchar(30) NULL,
	phone varchar(20) NULL,
	email varchar(30) NULL,
	PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS person (
	id int NOT NULL AUTO_INCREMENT,
	`name` varchar(60) NOT NULL,
	full_name varchar(60) NOT NULL,
	actor_id int NULL,
	project_id int NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY (actor_id) REFERENCES actor(id) ON DELETE SET NULL,
	FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `block` (
	id int NOT NULL AUTO_INCREMENT,
	`order` int NOT NULL,
	content TEXT NOT NULL,
	person_id int NULL,
	scene_id int NOT NULL,
	PRIMARY KEY (id),
	FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE SET NULL,
	FOREIGN KEY (scene_id) REFERENCES scene(id) ON DELETE CASCADE
);
