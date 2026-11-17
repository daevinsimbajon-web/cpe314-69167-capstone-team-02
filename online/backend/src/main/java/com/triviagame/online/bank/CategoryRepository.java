package com.triviagame.online.bank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Reads tree nodes using the authenticated owner, never a client-supplied account identity. */
@Repository
public class CategoryRepository {
    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoryBranch> list(UUID owner) {
        return jdbc.query(
                """
                SELECT id,name,parent_id FROM personal_quiz_categories
                WHERE owner_user_id=? ORDER BY name,id
                """,
                (rs, row) ->
                        new CategoryBranch(
                                rs.getObject("id", UUID.class),
                                rs.getString("name"),
                                rs.getObject("parent_id", UUID.class)),
                owner);
    }

    public List<CategoryBranch> named(UUID owner, String normalized) {
        return jdbc.query(
                """
                SELECT id,name,parent_id FROM personal_quiz_categories
                WHERE owner_user_id=? AND normalized_name=?
                """,
                (rs, row) ->
                        new CategoryBranch(
                                rs.getObject("id", UUID.class),
                                rs.getString("name"),
                                rs.getObject("parent_id", UUID.class)),
                owner,
                normalized);
    }

    public void create(
            UUID owner, UUID id, String name, String normalized, UUID parent, OffsetDateTime now) {
        jdbc.update(
                """
                INSERT INTO personal_quiz_categories(id,owner_user_id,name,normalized_name,created_at,parent_id)
                VALUES(?,?,?,?,?,?)
                """,
                id,
                owner,
                name,
                normalized,
                now,
                parent);
    }

    public boolean owns(UUID owner, UUID id) {
        return jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM personal_quiz_categories WHERE id=? AND owner_user_id=?
                        """,
                        Integer.class,
                        id,
                        owner)
                > 0;
    }

    public String name(UUID owner, UUID id) {
        return jdbc.queryForObject(
                "SELECT name FROM personal_quiz_categories WHERE id=? AND owner_user_id=?",
                String.class,
                id,
                owner);
    }
}
