-- V52 created token_hash as CHAR(64) — a SHA-256 hex digest is always exactly
-- that long, so the fixed width looked right. But the entity maps it as a
-- plain String with length 64, which Hibernate expects to find as VARCHAR(64),
-- and prod runs with ddl-auto: validate. The mismatch failed the startup
-- schema check, so the app never became healthy and the deploy was rolled
-- back. Every other string column in this schema is VARCHAR; this brings the
-- one exception into line rather than teaching the mapping about it.
ALTER TABLE api_token MODIFY COLUMN token_hash VARCHAR(64) NOT NULL;

-- The same table's timestamps are the other half of the drift. MySQL's
-- TIMESTAMP converts to and from the session's timezone on every read and
-- write, and runs out of range in 2038; DATETIME stores what it was given and
-- is what password_recovery_token and every other table here uses. No rows to
-- convert — a passkey sign-in has never reached this deployment, so the table
-- is empty.
ALTER TABLE api_token MODIFY COLUMN created DATETIME NOT NULL;
ALTER TABLE api_token MODIFY COLUMN last_used DATETIME NULL;
