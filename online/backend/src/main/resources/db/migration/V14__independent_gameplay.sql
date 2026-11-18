-- Additive: existing rooms, sessions, scores and answer history keep their original rules.
ALTER TABLE quiz_rooms ADD COLUMN independent_flow BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quiz_rooms ADD COLUMN reveal_answers BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE quiz_rooms ADD COLUMN starts_at TIMESTAMP WITH TIME ZONE;
CREATE TABLE room_player_progress (
    room_id UUID NOT NULL REFERENCES quiz_rooms(id),
    user_id UUID NOT NULL REFERENCES users(id),
    ordinal INTEGER NOT NULL DEFAULT 0,
    question_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    feedback_until TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY(room_id,user_id)
);
CREATE TABLE room_retry_decisions (
    user_id UUID NOT NULL REFERENCES users(id),
    submission_id UUID NOT NULL,
    use_chance BOOLEAN NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(user_id,submission_id),
    FOREIGN KEY(user_id,submission_id) REFERENCES answer_attempts(user_id,submission_id)
);
CREATE TABLE room_question_presentations (
    room_question_id UUID NOT NULL REFERENCES room_questions(id),
    user_id UUID NOT NULL REFERENCES users(id),
    presented_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(room_question_id,user_id)
);
