-- Long-lived opaque API tokens for native clients (e.g. the SwiftUI app).
-- A passkey sign-in mints one of these; the client then authenticates every
-- request with `Authorization: Bearer <token>` instead of a password, keeping
-- the client stateless and cookieless. Only the SHA-256 hash of the token is
-- stored, so a database leak does not expose usable credentials.
CREATE TABLE IF NOT EXISTS api_token (
	id INT AUTO_INCREMENT PRIMARY KEY,
	token_hash VARCHAR(64) NOT NULL UNIQUE,
	username VARCHAR(100) NOT NULL,
	label VARCHAR(100) NULL,
	created_at TIMESTAMP NOT NULL,
	last_used_at TIMESTAMP NULL,
	expires_at TIMESTAMP NULL,
	INDEX idx_api_token_username (username)
);
