package com.triviagame.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triviagame.api.BankQuestion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

        Database.initializeSchema();

        // Offline fallback: existing 15 sample questions
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

        initializeBank();

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

        CategoryDao categoryDao = new CategoryDao();
        QuestionDao questionDao = new QuestionDao();

        Map<String, Integer> categoryIds =
                new HashMap<>();

        for (var category : categoryDao.getAllCategories()) {
            categoryIds.put(
                normalize(category.name()),
                category.id()
            );
        }

        Set<String> questionKeys = new HashSet<>();

        for (var question : questionDao.getAllQuestions()) {
            questionKeys.add(
                question.categoryId()
                + "|" + normalize(question.text())
            );
        }

        int inserted = 0;

        for (BankQuestion q : input) {

            String categoryKey = normalize(q.category());

            Integer categoryId =
                    categoryIds.get(categoryKey);

            if (categoryId == null) {
                categoryId = categoryDao.addCategory(q.category());
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
                    q.text(),
                    q.correctAnswer(),
                    wrongAnswers,
                    q.difficulty(),
                    categoryId,
                    q.questionType()
                );

                inserted++;

            } catch (JsonProcessingException e) {
                throw new SQLException(
                    "Could not serialize incorrect answers", e
                );
            }
        }

        try (
            Connection c = Database.getConnection();
            PreparedStatement ps = c.prepareStatement(
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

    private static String normalize(String value) {
        return value.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}