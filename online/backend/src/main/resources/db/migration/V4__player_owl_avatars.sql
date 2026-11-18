-- Nullable for existing accounts: the public DTO derives a stable UUID fallback.
-- New accounts persist a complete validated configuration at registration.
ALTER TABLE users ADD COLUMN avatar_config VARCHAR(500);
