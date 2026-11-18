-- Retain anonymous roster/score references so deleting an account never destroys
-- another participant's results. Credentials and personal fields are erased.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
