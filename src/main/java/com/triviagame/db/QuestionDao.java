package com.triviagame.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuestionDao {

    // Database representation of a trivia question
    public record QuestionRecord(
    		int id,
            String text,
            String correctAnswer,
            String incorrectAnswers,
            String difficulty,
            int categoryId,
            String questionType
    ) {
    }

    // INSERT: Save a new trivia question
    public int addQuestion(
            String text,
            String correctAnswer,
            String incorrectAnswers,
            String difficulty,
            int categoryId
    ) throws SQLException {

        return addQuestion(
            text,
            correctAnswer,
            incorrectAnswers,
            difficulty,
            categoryId,
            "MULTIPLE_CHOICE"
        );
    }
    
    public int addQuestion(
            String text,
            String correctAnswer,
            String incorrectAnswers,
            String difficulty,
            int categoryId,
            String questionType
    ) throws SQLException {

        validateQuestion(
            text,
            correctAnswer,
            incorrectAnswers,
            difficulty,
            categoryId
        );

        if (!"MULTIPLE_CHOICE".equals(questionType)
                && !"TRUE_FALSE".equals(questionType)) {

            throw new IllegalArgumentException(
                "Invalid question type"
            );
        }

        String sql = """
            INSERT INTO questions (
                text,
                correct_answer,
                incorrect_answers,
                difficulty,
                category_id,
                question_type
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, text.trim());
            statement.setString(2, correctAnswer.trim());
            statement.setString(3, incorrectAnswers.trim());
            statement.setString(4, difficulty.trim());
            statement.setInt(5, categoryId);
            statement.setString(6, questionType);

            int rows = statement.executeUpdate();

            if (rows != 1) {
                throw new SQLException(
                    "Failed to insert question"
                );
            }

            String idSql =
                "SELECT last_insert_rowid() AS id";

            try (PreparedStatement idStatement =
                         connection.prepareStatement(idSql);
                 ResultSet result =
                         idStatement.executeQuery()) {

                if (result.next()) {
                    return result.getInt("id");
                }

                throw new SQLException(
                    "Question ID could not be retrieved"
                );
            }
        }
    }

    // SELECT: Find question by ID
    public Optional<QuestionRecord> getQuestionById(
            int id
    ) throws SQLException {

        String sql = """
            SELECT *
            FROM questions
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet result =
                         statement.executeQuery()) {

                if (result.next()) {
                    return Optional.of(
                            mapQuestion(result)
                    );
                }
            }
        }

        return Optional.empty();
    }

    // SELECT: Get all saved questions
    public List<QuestionRecord> getAllQuestions()
            throws SQLException {

        List<QuestionRecord> questions =
                new ArrayList<>();

        String sql = """
            SELECT *
            FROM questions
            ORDER BY id ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result =
                     statement.executeQuery()) {

            while (result.next()) {
                questions.add(
                        mapQuestion(result)
                );
            }
        }

        return questions;
    }

    // SELECT: Filter questions by category
    public List<QuestionRecord> getQuestionsByCategory(
            int categoryId
    ) throws SQLException {

        List<QuestionRecord> questions =
                new ArrayList<>();

        String sql = """
            SELECT *
            FROM questions
            WHERE category_id = ?
            ORDER BY id ASC
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, categoryId);

            try (ResultSet result =
                         statement.executeQuery()) {

                while (result.next()) {
                    questions.add(
                            mapQuestion(result)
                    );
                }
            }
        }

        return questions;
    }

    // UPDATE: Modify an existing question
    public boolean updateQuestion(
            int id,
            String text,
            String correctAnswer,
            String incorrectAnswers,
            String difficulty,
            int categoryId
    ) throws SQLException {

        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Question ID must be positive"
            );
        }

        validateQuestion(
                text,
                correctAnswer,
                incorrectAnswers,
                difficulty,
                categoryId
        );

        String sql = """
            UPDATE questions
            SET text = ?,
                correct_answer = ?,
                incorrect_answers = ?,
                difficulty = ?,
                category_id = ?
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, text.trim());
            statement.setString(2, correctAnswer.trim());
            statement.setString(3, incorrectAnswers.trim());
            statement.setString(4, difficulty.trim());
            statement.setInt(5, categoryId);
            statement.setInt(6, id);

            return statement.executeUpdate() > 0;
        }
    }

    // DELETE: Remove a question by ID
    public boolean deleteQuestion(int id)
            throws SQLException {

        String sql = """
            DELETE FROM questions
            WHERE id = ?
            """;

        try (Connection connection =
                     Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            return statement.executeUpdate() > 0;
        }
    }

    // Convert ResultSet into QuestionRecord
    private QuestionRecord mapQuestion(ResultSet result)
            throws SQLException {

        return new QuestionRecord(
            result.getInt("id"),
            result.getString("text"),
            result.getString("correct_answer"),
            result.getString("incorrect_answers"),
            result.getString("difficulty"),
            result.getInt("category_id"),
            result.getString("question_type")
        );
    }

    // Validate question details before INSERT or UPDATE
    private void validateQuestion(
            String text,
            String correctAnswer,
            String incorrectAnswers,
            String difficulty,
            int categoryId
    ) {

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Question text cannot be empty"
            );
        }

        if (correctAnswer == null
                || correctAnswer.isBlank()) {
            throw new IllegalArgumentException(
                    "Correct answer cannot be empty"
            );
        }

        if (incorrectAnswers == null
                || incorrectAnswers.isBlank()) {
            throw new IllegalArgumentException(
                    "Incorrect answers cannot be empty"
            );
        }

        if (difficulty == null
                || difficulty.isBlank()) {
            throw new IllegalArgumentException(
                    "Difficulty cannot be empty"
            );
        }

        if (categoryId <= 0) {
            throw new IllegalArgumentException(
                    "Category ID must be positive"
            );
        }
    }
}
