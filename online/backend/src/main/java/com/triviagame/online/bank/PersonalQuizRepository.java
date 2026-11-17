package com.triviagame.online.bank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns SQL for private quiz collections and their immutable question versions. */
@Repository
public class PersonalQuizRepository {
    private final JdbcTemplate jdbc;

    public PersonalQuizRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(UUID owner) {
        return jdbc.queryForList(
                """
                SELECT q.*,c.name AS category_name FROM quiz_collections q
                LEFT JOIN personal_quiz_categories c ON c.id=q.custom_category_id
                WHERE q.owner_user_id=? AND q.deleted_at IS NULL ORDER BY q.updated_at DESC,q.id
                """,
                owner);
    }

    public List<Map<String, Object>> owned(UUID owner, UUID id, boolean lock) {
        return jdbc.queryForList(
                """
                SELECT q.*,c.name AS category_name FROM quiz_collections q
                LEFT JOIN personal_quiz_categories c ON c.id=q.custom_category_id
                WHERE q.id=? AND q.owner_user_id=? AND q.deleted_at IS NULL
                """
                        + (lock ? " FOR UPDATE OF q" : ""),
                id,
                owner);
    }

    public void create(
            UUID id,
            UUID owner,
            String title,
            String description,
            UUID category,
            String status,
            String document,
            OffsetDateTime now) {
        jdbc.update(
                """
                INSERT INTO quiz_collections(id,owner_user_id,title,description,custom_category_id,
                status,version,draft_document,created_at,updated_at) VALUES(?,?,?,?,?,?,1,?,?,?)
                """,
                id,
                owner,
                title,
                description,
                category,
                status,
                document,
                now,
                now);
    }

    public void save(
            UUID id,
            String title,
            String description,
            UUID category,
            String status,
            String document,
            OffsetDateTime now) {
        jdbc.update(
                """
                UPDATE quiz_collections SET title=?,description=?,custom_category_id=?,status=?,
                version=version+1,draft_document=?,updated_at=? WHERE id=?
                """,
                title,
                description,
                category,
                status,
                document,
                now,
                id);
    }

    public void unlink(UUID quiz) {
        jdbc.update("DELETE FROM quiz_collection_questions WHERE quiz_id=?", quiz);
    }

    public void retire(UUID quiz, OffsetDateTime now) {
        jdbc.update(
                "UPDATE quiz_collections SET deleted_at=?,updated_at=? WHERE id=?", now, now, quiz);
    }

    public List<Map<String, Object>> library(UUID owner, List<UUID> branch) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(owner);
        String filter = "";
        if (branch != null) {
            filter =
                    """
                    AND EXISTS (SELECT 1 FROM quiz_collection_questions link
                    JOIN quiz_collections quiz ON quiz.id=link.quiz_id WHERE link.question_id=q.id
                    AND quiz.owner_user_id=? AND quiz.deleted_at IS NULL AND quiz.custom_category_id IN (
                    """
                            + String.join(",", Collections.nCopies(branch.size(), "?"))
                            + "))";
            parameters.add(owner);
            parameters.addAll(branch);
        }
        return jdbc.queryForList(
                """
                SELECT q.*,c.name AS category_name FROM questions q JOIN categories c ON c.id=q.category_id
                WHERE q.owner_user_id=?
                """
                        + filter
                        + " ORDER BY q.created_at DESC,q.id LIMIT 1000",
                parameters.toArray());
    }

    public List<Map<String, Object>> playable(UUID owner, UUID quiz) {
        return jdbc.queryForList(
                """
                SELECT q.*,c.name AS category_name FROM quiz_collection_questions link
                JOIN questions q ON q.id=link.question_id JOIN categories c ON c.id=q.category_id
                WHERE link.quiz_id=? AND q.owner_user_id=? ORDER BY link.display_order
                """,
                quiz,
                owner);
    }

    public long storageCategory(String name, String normalized) {
        jdbc.queryForObject("SELECT id FROM import_mutex WHERE id=1 FOR UPDATE", Integer.class);
        var ids =
                jdbc.queryForList(
                        "SELECT id FROM categories WHERE normalized_name=?",
                        Long.class,
                        normalized);
        if (ids.isEmpty()) {
            jdbc.update(
                    "INSERT INTO categories(name,normalized_name) VALUES(?,?)", name, normalized);
        }
        return jdbc.queryForObject(
                "SELECT id FROM categories WHERE normalized_name=?", Long.class, normalized);
    }

    public List<UUID> existingQuestion(UUID owner, String key) {
        return jdbc.queryForList(
                "SELECT id FROM questions WHERE content_key=? AND owner_user_id=?",
                UUID.class,
                key,
                owner);
    }

    public void question(
            UUID id,
            UUID owner,
            long category,
            String text,
            String correct,
            String wrong,
            String difficulty,
            String type,
            String key) {
        jdbc.update(
                """
                INSERT INTO questions(id,category_id,text,correct_answer,incorrect_answers,difficulty,
                question_type,external_id,content_key,owner_user_id) VALUES(?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                category,
                text,
                correct,
                wrong,
                difficulty,
                type,
                "custom:" + key,
                key,
                owner);
    }

    public void link(UUID quiz, UUID question, int ordinal) {
        jdbc.update(
                "INSERT INTO quiz_collection_questions(quiz_id,question_id,display_order)"
                    + " VALUES(?,?,?)",
                quiz,
                question,
                ordinal);
    }

    public boolean lockOwner(UUID owner) {
        return !jdbc.queryForList(
                        "SELECT id FROM users WHERE id=? AND deleted_at IS NULL FOR UPDATE",
                        UUID.class,
                        owner)
                .isEmpty();
    }
}
