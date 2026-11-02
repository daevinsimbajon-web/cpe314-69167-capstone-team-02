package com.triviagame;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.Database;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import com.triviagame.db.SampleQuestionSeeder;
import com.triviagame.db.ScoreDao;

import com.triviagame.gameplay.GameEngine;
import com.triviagame.home.Home;
import com.triviagame.questions.Question;
import com.triviagame.questions.QuestionMapper;

public class Main {

    public static void main(String[] args) {
    App.main(args);
}

    public static void startGame(Scanner scanner)
        throws SQLException {

        Database.initializeSchema();

        // Insert sample questions if they do not exist.
        SampleQuestionSeeder.seed();

        PlayerDao playerDao = new PlayerDao();
        CategoryDao categoryDao = new CategoryDao();
        QuestionDao questionDao = new QuestionDao();
        ScoreDao scoreDao = new ScoreDao();

        System.out.println("=== JAVA TRIVIA GAME ===");

        System.out.print("Enter player name: ");
        String name = scanner.nextLine();

        while (name.isBlank()) {
            System.out.print("Please enter a valid name: ");
            name = scanner.nextLine();
        }

        int playerId = playerDao.addPlayer(name);

        System.out.println("\nAvailable Categories:");

        List<CategoryDao.CategoryRecord> categories =
                categoryDao.getAllCategories();

        for (CategoryDao.CategoryRecord category : categories) {

            System.out.println(
                category.id() + ". " + category.name()
            );
        }

        System.out.print("\nEnter Category ID: ");

        int categoryId;

        try {
            categoryId = Integer.parseInt(
                scanner.nextLine().trim()
            );
        } catch (NumberFormatException e) {
            System.out.println("Invalid category ID.");
            return;
        }

        if (categoryDao.getCategoryById(categoryId).isEmpty()) {
            System.out.println("Category not found.");
            return;
        }

        // Recursive retrieval from SQLite
        var records =
            categoryDao.getQuestionsRecursively(categoryId);

        if (records.isEmpty()) {
            System.out.println(
                "No questions available in this category."
            );
            return;
        }

        List<Question> questions = new ArrayList<>();

        for (QuestionDao.QuestionRecord record : records) {
            questions.add(QuestionMapper.toQuestion(record));
        }

        // Create a new database game session.
        int sessionId = scoreDao.createGameSession();

        GameEngine game = new GameEngine(playerId, sessionId);

        game.loadQuestions(questions);

        System.out.println("\nGame started!");
        System.out.println("Total Questions: " + questions.size());

        while (game.hasNextQuestion()) {

            System.out.println("\n---------------------");

            Question question = game.nextQuestion();

            // Polymorphic display
            question.display();

            System.out.print("\nYour answer (or 'undo'): ");

            String answer = scanner.nextLine();

            if (answer.equalsIgnoreCase("undo")) {

                if (game.undo()) {
                    System.out.println("Previous answer undone.");
                } else {
                    System.out.println("Nothing to undo.");
                }

                continue;
            }

            boolean correct = game.submitAnswer(answer);

            System.out.println(
                correct ? "Correct!" : "Incorrect!"
            );

            System.out.println("Current Score: " + game.getScore());

            if (game.hasNextQuestion()) {

                System.out.print(
                    "Undo previous answer? (yes/no): "
                );

                if (scanner.nextLine().equalsIgnoreCase("yes")) {
                    game.undo();
                    System.out.println("Answer undone.");
                }
            }
        }

        System.out.println("\n===== GAME OVER =====");

        System.out.println("Player: " + name);

        System.out.println(
            "Final Score: " + game.getScore()
        );

        System.out.println(
            "Saved Database Score: " +
            scoreDao.getScoresBySession(sessionId)
                .stream()
                .filter(s -> s.playerId() == playerId)
                .mapToInt(s -> s.points())
                .sum()
        );

        System.out.println("Session ID: " + sessionId);
    }
}
