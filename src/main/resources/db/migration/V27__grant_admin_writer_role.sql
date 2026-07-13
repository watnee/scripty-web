-- Admins should be able to edit scripts and insert songs/drafts.
INSERT INTO authority (username, authority)
SELECT 'admin', 'ROLE_WRITER'
WHERE EXISTS (SELECT 1 FROM `user` WHERE username = 'admin')
  AND NOT EXISTS (
    SELECT 1 FROM authority WHERE username = 'admin' AND authority = 'ROLE_WRITER'
);
