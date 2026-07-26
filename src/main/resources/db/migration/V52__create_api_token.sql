-- Bearer tokens for native API clients signed in with a passkey. A passkey
-- sign-in proves who you are but leaves the client with no password to put in
-- a Basic header, so the API mints one of these instead. Only the SHA-256 of
-- the token is stored; the raw value is shown to the client once. Keyed by
-- username without a foreign key, matching persistent_logins: a deleted user's
-- tokens simply stop resolving.

CREATE TABLE IF NOT EXISTS api_token (
	id BIGINT NOT NULL AUTO_INCREMENT,
	username VARCHAR(100) NOT NULL,
	token_hash CHAR(64) NOT NULL,
	label VARCHAR(200) NULL,
	created TIMESTAMP NOT NULL,
	last_used TIMESTAMP NULL,
	PRIMARY KEY (id),
	UNIQUE INDEX idx_api_token_hash (token_hash),
	INDEX idx_api_token_username (username)
);
