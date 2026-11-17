CREATE TABLE personal_quiz_categories (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(owner_user_id, normalized_name)
);
CREATE TABLE quiz_collections (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    custom_category_id UUID REFERENCES personal_quiz_categories(id),
    visibility VARCHAR(10) NOT NULL DEFAULT 'PRIVATE' CHECK(visibility='PRIVATE'),
    status VARCHAR(10) NOT NULL CHECK(status IN ('DRAFT','READY')),
    version INTEGER NOT NULL CHECK(version > 0),
    draft_document TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_quizzes_owner ON quiz_collections(owner_user_id, deleted_at, updated_at);
ALTER TABLE questions ADD COLUMN owner_user_id UUID REFERENCES users(id);
CREATE INDEX ix_personal_questions ON questions(owner_user_id, category_id);
CREATE TABLE quiz_collection_questions (
    quiz_id UUID NOT NULL REFERENCES quiz_collections(id),
    question_id UUID NOT NULL REFERENCES questions(id),
    display_order INTEGER NOT NULL CHECK(display_order >= 0),
    PRIMARY KEY(quiz_id, display_order)
);
ALTER TABLE quiz_rooms ADD COLUMN custom_quiz_id UUID REFERENCES quiz_collections(id);
ALTER TABLE quiz_rooms ADD COLUMN custom_quiz_version INTEGER;
