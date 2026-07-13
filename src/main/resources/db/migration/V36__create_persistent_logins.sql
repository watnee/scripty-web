-- Spring Security persistent remember-me tokens (JdbcTokenRepositoryImpl schema).
-- Lets logins survive server restarts/deploys instead of signing everyone out.
CREATE TABLE IF NOT EXISTS persistent_logins (
	username VARCHAR(64) NOT NULL,
	series VARCHAR(64) NOT NULL,
	token VARCHAR(64) NOT NULL,
	last_used TIMESTAMP NOT NULL,
	PRIMARY KEY (series)
);
