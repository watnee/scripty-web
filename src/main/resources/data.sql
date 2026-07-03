INSERT IGNORE INTO `user` (id, username, `password`, enabled, first_name, last_name) VALUES
(1, 'admin', '$2a$10$BGmcUaHCftiGRKB2d8XCWO2u04qbdsVt.xoq1gw1f2ILA6TaRpA2G', 1, 'Chris', 'Watnee'),
(2, 'clint', '$2a$10$BGmcUaHCftiGRKB2d8XCWO2u04qbdsVt.xoq1gw1f2ILA6TaRpA2G', 1, 'Clint', 'Watnee');

INSERT IGNORE INTO authority (username, authority) VALUES
('admin', 'ROLE_ADMIN'),
('admin', 'ROLE_USER'),
('clint', 'ROLE_USER');
