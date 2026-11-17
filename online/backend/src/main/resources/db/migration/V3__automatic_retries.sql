-- Historical games keep their original scores and unknown attempt/timing metrics.
ALTER TABLE quiz_rooms ADD COLUMN retry_allowance INTEGER NOT NULL DEFAULT 0 CHECK(retry_allowance BETWEEN 0 AND 10);
ALTER TABLE quiz_rooms ADD COLUMN scoring_version VARCHAR(30) NOT NULL DEFAULT 'legacy-v1';
ALTER TABLE quiz_rooms ADD COLUMN question_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE room_questions ADD COLUMN presented_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE scores ADD COLUMN retries_remaining INTEGER NOT NULL DEFAULT 0 CHECK(retries_remaining BETWEEN 0 AND 10);
ALTER TABLE answers ALTER COLUMN selected_index DROP NOT NULL;
ALTER TABLE answers ADD COLUMN timed_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE answers ADD COLUMN first_try_correct BOOLEAN;
ALTER TABLE answers ADD COLUMN attempt_count INTEGER;
ALTER TABLE answers ADD COLUMN retries_used INTEGER;
ALTER TABLE answers ADD COLUMN elapsed_ms BIGINT;
CREATE TABLE answer_attempts (
    id UUID PRIMARY KEY,
    room_question_id UUID NOT NULL REFERENCES room_questions(id),
    user_id UUID NOT NULL REFERENCES users(id),
    submission_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL CHECK(attempt_number > 0),
    selected_index INTEGER NOT NULL CHECK(selected_index >= 0),
    is_correct BOOLEAN NOT NULL,
    outcome VARCHAR(12) NOT NULL CHECK(outcome IN ('RETRY','CORRECT','INCORRECT')),
    retries_remaining INTEGER NOT NULL CHECK(retries_remaining BETWEEN 0 AND 10),
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(user_id, submission_id),
    UNIQUE(room_question_id, user_id, attempt_number)
);
CREATE INDEX ix_attempts_user_question ON answer_attempts(user_id, room_question_id);
CREATE INDEX ix_sessions_finished ON game_sessions(finished_at);
