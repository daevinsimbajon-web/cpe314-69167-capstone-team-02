
package com.triviagame.db;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QuestionDaoTest {

    @BeforeAll
    static void setupDatabase() throws SQLException {
        Database.initializeSchema();
    }

    @Test
    void testQuestionCRUD() throws SQLException {

        CategoryDao categoryDao = new CategoryDao();
        QuestionDao questionDao = new QuestionDao();

        int categoryId = categoryDao.addCategory(
                "Science_" + UUID.randomUUID()
        );

        int questionId = 0;

        try {
            // INSERT
            questionId = questionDao.addQuestion(
                    "What planet is known as the Red Planet?",
                    "Mars",
                    "[\"Earth\",\"Venus\",\"Jupiter\"]",
                    "easy",
                    categoryId
            );

            assertTrue(questionId > 0);

            // SELECT
            QuestionDao.QuestionRecord question =
                    questionDao.getQuestionById(questionId)
                            .orElseThrow();

            assertEquals(
                    "Mars",
                    question.correctAnswer()
            );

            assertEquals(
                    "easy",
                    question.difficulty()
            );

            assertEquals(
                    categoryId,
                    question.categoryId()
            );

            // UPDATE
            assertTrue(
                    questionDao.updateQuestion(
                            questionId,
                            "Which planet is called the Red Planet?",
                            "Mars",
                            "[\"Earth\",\"Venus\",\"Jupiter\"]",
                            "medium",
                            categoryId
                    )
            );

            QuestionDao.QuestionRecord updated =
                    questionDao.getQuestionById(questionId)
                            .orElseThrow();

            assertEquals(
                    "medium",
                    updated.difficulty()
            );

            // CATEGORY FILTER
            int savedId = questionId;

            assertTrue(
                    questionDao
                            .getQuestionsByCategory(categoryId)
                            .stream()
                            .anyMatch(q -> q.id() == savedId)
            );

            // ALL QUESTIONS
            assertTrue(
                    questionDao.getAllQuestions()
                            .stream()
                            .anyMatch(q -> q.id() == savedId)
            );

        } finally {

            if (questionId > 0) {
                questionDao.deleteQuestion(questionId);
            }

            categoryDao.deleteCategory(categoryId);
        }

        // Confirm deletion
        if (questionId > 0) {
            assertTrue(
                    questionDao
                            .getQuestionById(questionId)
                            .isEmpty()
            );
        }
    }

    @Test
    void testInvalidQuestion() throws SQLException {

        QuestionDao dao = new QuestionDao();

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addQuestion(
                        "",
                        "Mars",
                        "[\"Earth\"]",
                        "easy",
                        1
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addQuestion(
                        "What is 2 + 2?",
                        "",
                        "[\"3\",\"5\"]",
                        "easy",
                        1
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> dao.addQuestion(
                        "What is 2 + 2?",
                        "4",
                        "[\"3\",\"5\"]",
                        "easy",
                        -1
                )
        );
    }

    @Test
    void testNonexistentQuestion()
            throws SQLException {

        QuestionDao dao = new QuestionDao();

        assertTrue(
                dao.getQuestionById(
                        Integer.MAX_VALUE
                ).isEmpty()
        );

        assertFalse(
                dao.deleteQuestion(
                        Integer.MAX_VALUE
                )
        );
    }
}
    