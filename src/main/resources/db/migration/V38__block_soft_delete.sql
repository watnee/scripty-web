-- Soft delete for blocks: deleting keeps the row for 30 days (restorable
-- from the project's Recently Deleted page) before a scheduled job purges it.
ALTER TABLE `block` ADD COLUMN deleted_at TIMESTAMP NULL;

CREATE INDEX idx_block_deleted_at ON `block` (deleted_at);
