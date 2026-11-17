-- Instant Quiz questions belong to the game, not to a reusable question bank.
-- Existing bank links and historical records are retained unchanged.
ALTER TABLE room_questions ALTER COLUMN question_id DROP NOT NULL;
ALTER TABLE room_questions ADD COLUMN category_name VARCHAR(200);
ALTER TABLE room_questions ADD CONSTRAINT room_question_category_available
    CHECK(question_id IS NOT NULL OR category_name IS NOT NULL);
