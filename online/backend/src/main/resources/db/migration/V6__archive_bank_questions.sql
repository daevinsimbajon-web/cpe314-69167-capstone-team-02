-- Archived bank entries remain available to historical room snapshots and analytics.
ALTER TABLE questions ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
