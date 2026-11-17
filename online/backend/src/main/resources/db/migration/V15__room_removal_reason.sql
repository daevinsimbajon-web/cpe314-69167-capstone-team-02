-- Preserve existing participation records; record only future explicit host removals.
ALTER TABLE room_participants ADD COLUMN removed_by_host BOOLEAN NOT NULL DEFAULT FALSE;
