-- A password recovery token is a bearer credential: whoever holds the string
-- can take over the account it belongs to. It was stored here verbatim, so a
-- database read — a backup on a laptop, a dump pasted into a support ticket,
-- one over-broad SELECT — was enough to do that. api_token has stored only a
-- SHA-256 since V52; this brings the older table into line.
--
-- Existing rows cannot be converted. Hashing a stored token would produce a
-- digest of a digest, and the raw value it was minted from now exists only in
-- the recipient's inbox, so there is nothing to hash. They are deleted instead.
-- The cost is that a reset link already in flight stops working and has to be
-- requested again — bounded by the two-hour expiry these carry anyway.
DELETE FROM password_recovery_token;

-- The column keeps its type, its NOT NULL and its unique key; only the name
-- changes, so that a reader can tell at a glance what is in it. A SHA-256 hex
-- digest is 64 characters, exactly the width already declared.
ALTER TABLE password_recovery_token RENAME COLUMN token TO token_hash;
