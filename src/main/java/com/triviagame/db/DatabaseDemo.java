
package com.triviagame.db;

import java.sql.SQLException;
import java.util.UUID;

public class DatabaseDemo {

    public static void main(String[] args)
            throws SQLException {

        // Initialize SQLite tables
        Database.initializeSchema();

        PlayerDao playerDao = new PlayerDao();
        CategoryDao categoryDao = new CategoryDao();
        QuestionDao questionDao = new QuestionDao();
        ScoreDao scoreDao = new ScoreDao();

        System.out.println("==========================");
        System.out.println("TRIVIA GAME DATABASE DEMO");
        System.out.println("==========================");

        // Unique name for each demonstration
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 8);

        // 1. INSERT PLAYER
        int playerId = playerDao.addPlayer(
                "DemoPlayer_" + suffix
        );

        System.out.println(
                "Player created! ID: " + playerId
        );

        // 2. INSERT CATEGORY
        int categoryId = categoryDao.addCategory(
                "Science_" + suffix
        );

        System.out.println(
                "Category created! ID: " + categoryId
        );

        // 3. INSERT QUESTION
        int questionId = questionDao.addQuestion(
                "What planet is known as the Red Planet?",
                "Mars",
                "[\"Earth\",\"Venus\",\"Jupiter\"]",
                "easy",
                categoryId
        );

        System.out.println(
                "Question created! ID: " + questionId
        );

        // 4. CREATE GAME SESSION
        int sessionId =
                scoreDao.createGameSession();

        System.out.println(
                "Game session created! ID: " + sessionId
        );

        // 5. INSERT SCORE
        int scoreId = scoreDao.addScore(
                sessionId,
                playerId,
                categoryId,
                questionId,
                true,
                10
        );

        System.out.println(
                "Score saved! ID: " + scoreId
        );

        // 6. SELECT PLAYER
        var player = playerDao
                .getPlayerById(playerId)
                .orElseThrow();

        System.out.println(
                "Retrieved player: " + player.name()
        );

        // 7. SELECT QUESTION
        var question = questionDao
                .getQuestionById(questionId)
                .orElseThrow();

        System.out.println(
                "Retrieved question: " + question.text()
        );

        System.out.println(
                "Correct answer: " + question.correctAnswer()
        );

        // 8. SELECT SCORE
        var score = scoreDao
                .getScoreById(scoreId)
                .orElseThrow();

        System.out.println(
                "Retrieved points: " + score.points()
        );

        // 9. TOTAL PLAYER SCORE
        long totalPoints =
                scoreDao.getTotalPointsByPlayer(playerId);

        System.out.println(
                "Total player points: " + totalPoints
        );

        System.out.println("==========================");
        System.out.println("DATABASE DEMO SUCCESSFUL");
        System.out.println("==========================");
    }
}
