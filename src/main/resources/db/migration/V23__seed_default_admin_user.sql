INSERT INTO `user` (username, `password`, enabled, first_name, last_name, team, default_project_id)
SELECT 'admin',
       '$2a$10$U1BbvCsBTKijMlg.oicrZO8bD8tu/USryyDf7IrXi9PCghW2MFCuC',
       1,
       'Admin',
       'User',
       NULL,
       NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE username = 'admin'
);

INSERT INTO authority (username, authority)
SELECT 'admin', 'ROLE_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE username = 'admin' AND authority = 'ROLE_ADMIN'
);

INSERT INTO authority (username, authority)
SELECT 'admin', 'ROLE_USER'
WHERE NOT EXISTS (
    SELECT 1 FROM authority WHERE username = 'admin' AND authority = 'ROLE_USER'
);
