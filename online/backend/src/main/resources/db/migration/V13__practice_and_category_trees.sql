-- Additive changes only. Existing competitive scores and imported histories stay intact.
ALTER TABLE personal_quiz_categories ADD COLUMN parent_id UUID REFERENCES personal_quiz_categories(id);
CREATE INDEX ix_personal_category_parent ON personal_quiz_categories(owner_user_id, parent_id);
ALTER TABLE scores ADD COLUMN forfeited BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE quiz_practice_sessions (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    quiz_id UUID NOT NULL REFERENCES quiz_collections(id),
    state_document TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    last_action_id UUID,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_practice_owner ON quiz_practice_sessions(owner_id, expires_at);
