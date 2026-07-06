-- Reset seeded dev passwords to bcrypt hash for "admin"
UPDATE `user`
SET `password` = '$2a$10$U1BbvCsBTKijMlg.oicrZO8bD8tu/USryyDf7IrXi9PCghW2MFCuC'
WHERE username IN ('admin', 'clint');
