-- Soft delete: trashed projects keep this timestamp and are purged after 30 days.
ALTER TABLE project ADD COLUMN deleted_at TIMESTAMP NULL;
