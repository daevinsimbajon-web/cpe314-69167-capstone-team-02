package com.triviagame.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triviagame.api.BankQuestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class QuestionBankService {

    private static final ObjectMapper JSON =
            new ObjectMapper();

    public record ImportReport(
            int received,
            int inserted,
            int duplicates) {
    }

    public void initializeBank() throws SQLException {
        synchronized (QuestionBankService.class) {
            Database.initializeSchema();

            // Preserve the existing offline question bank.
            SampleQuestionSeeder.seed();

            try (
                Connection c = Database.getConnection();
                Statement st = c.createStatement()
            ) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS question_bank_imports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        source TEXT NOT NULL,
                        inserted_count INTEGER NOT NULL,
                        imported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            }
        }
    }

    public boolean apiPreloaded() throws SQLException {

        initializeBank();

        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM question_bank_imports WHERE source = ?"
            )
        ) {
            ps.setString(1, "OpenTDB");

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public ImportReport importQuestions(
            List<BankQuestion> input,
            String source) throws SQLException {

        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException(
                "No questions to import"
            );
        }

        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException(
                "Source cannot be blank"
            );
        }

        initializeBank();
        try (Connection connection = Database.getConnection();
             Statement transaction = connection.createStatement()) {
            // Acquire the write lock before reading categories or duplicate keys.
            // Competing imports see the committed result before making decisions.
            transaction.execute("BEGIN IMMEDIATE");
            try {
                ImportReport report = importQuestions(connection, input, source);
                transaction.execute("COMMIT");
                return report;
            } catch (SQLException | RuntimeException exception) {
                try {
                    transaction.execute("ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        }
    }

    private ImportReport importQuestions(Connection connection, List<BankQuestion> input,
            String source) throws SQLException {
        QuestionDao questionDao = new QuestionDao();

        Map<String, Integer> categoryIds =
                new HashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, name FROM categories ORDER BY id")) {
            while (result.next()) {
                categoryIds.put(normalize(result.getString("name")), result.getInt("id"));
            }
        }

        Set<String> questionKeys = new HashSet<>();

        Set<String> externalIds = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT category_id, text, external_id FROM questions")) {
            while (result.next()) {
                questionKeys.add(result.getInt("category_id") + "|" + normalize(result.getString("text")));
                externalIds.add(result.getString("external_id"));
            }
        }

        int inserted = 0;

        for (BankQuestion q : input) {
            String externalId = externalId(source, q);
            if (externalIds.contains(externalId)) {
                continue;
            }

            String categoryKey = normalize(q.category());

            Integer categoryId =
                    categoryIds.get(categoryKey);

            if (categoryId == null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO categories (name) VALUES (?)")) {
                    statement.setString(1, q.category());
                    statement.executeUpdate();
                }
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT last_insert_rowid()")) {
                    result.next();
                    categoryId = result.getInt(1);
                }
                categoryIds.put(categoryKey, categoryId);
            }

            String key =
                    categoryId + "|" + normalize(q.text());

            if (!questionKeys.add(key)) {
                continue;
            }

            try {
                String wrongAnswers =
                        JSON.writeValueAsString(
                            q.incorrectAnswers()
                        );

                questionDao.addQuestion(
                    connection,
                    q.text(),
                    q.correctAnswer(),
                    wrongAnswers,
                    q.difficulty(),
                    categoryId,
                    q.questionType(),
                    externalId
                );

                externalIds.add(externalId);
                inserted++;

            } catch (JsonProcessingException e) {
                throw new SQLException(
                    "Could not serialize incorrect answers", e
                );
            }
        }

        try (
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO question_bank_imports "
                + "(source, inserted_count) VALUES (?, ?)"
            )
        ) {
            ps.setString(1, source.trim());
            ps.setInt(2, inserted);
            ps.executeUpdate();
        }

        return new ImportReport(
            input.size(),
            inserted,
            input.size() - inserted
        );
    }

    public List<QuestionDao.QuestionRecord> getQuestions(
            int categoryId) throws SQLException {

        QuestionDao dao = new QuestionDao();

        return categoryId <= 0
                ? dao.getAllQuestions()
                : dao.getQuestionsByCategory(categoryId);
    }

    public int count() throws SQLException {
        return new QuestionDao().getAllQuestions().size();
    }

    static String externalId(String source, BankQuestion question) {
        // Length prefixes prevent ambiguous concatenations. Provider IDs are
        // namespaced by source; OpenTDB falls back to category + normalized text.
        String identity = question.externalId() == null
                ? "text:" + part(normalize(question.category())) + part(normalize(question.text()))
                : "id:" + part(question.externalId());
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((part(normalize(source)) + identity).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String part(String value) {
        return value.length() + ":" + value;
    }

    private static String normalize(String value) {
        return value.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
