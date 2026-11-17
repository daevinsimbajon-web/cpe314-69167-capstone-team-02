CREATE TABLE analytics_signing_keys (id INTEGER PRIMARY KEY, secret VARCHAR(100) NOT NULL);
CREATE TABLE compared_players (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), export_player_id VARCHAR(120) NOT NULL,
 verified_user_id UUID REFERENCES users(id), label VARCHAR(100) NOT NULL, player_json TEXT NOT NULL,
 exported_at TIMESTAMP WITH TIME ZONE NOT NULL, last_synced_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(owner_id, export_player_id)
);
CREATE TABLE compared_games (
 member_id UUID NOT NULL REFERENCES compared_players(id) ON DELETE CASCADE,
 game_id VARCHAR(120) NOT NULL, game_json TEXT NOT NULL, PRIMARY KEY(member_id,game_id)
);
CREATE TABLE statistics_sharing (
 id UUID PRIMARY KEY, requester_id UUID NOT NULL REFERENCES users(id), recipient_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(10) NOT NULL CHECK(status IN ('PENDING','ALLOWED','DENIED','REVOKED')),
 requested_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 granted_at TIMESTAMP WITH TIME ZONE, revoked_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(requester_id,recipient_id), CHECK(requester_id<>recipient_id)
);
CREATE INDEX statistics_sharing_inbox ON statistics_sharing(recipient_id,status);
CREATE TABLE statistics_import_jobs (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), expected_player VARCHAR(120), source_json TEXT,
 status VARCHAR(12) NOT NULL, progress INTEGER NOT NULL DEFAULT 0, message VARCHAR(1000),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX statistics_import_owner ON statistics_import_jobs(owner_id,created_at);
