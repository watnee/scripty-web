INSERT IGNORE INTO `user` (id, username, `password`, enabled, first_name, last_name) VALUES
(1, 'admin', '$2a$10$U1BbvCsBTKijMlg.oicrZO8bD8tu/USryyDf7IrXi9PCghW2MFCuC', 1, 'Chris', 'Watnee'),
(2, 'clint', '$2a$10$U1BbvCsBTKijMlg.oicrZO8bD8tu/USryyDf7IrXi9PCghW2MFCuC', 1, 'Clint', 'Watnee');

INSERT IGNORE INTO authority (username, authority) VALUES
('admin', 'ROLE_ADMIN'),
('admin', 'ROLE_USER'),
('clint', 'ROLE_USER');
