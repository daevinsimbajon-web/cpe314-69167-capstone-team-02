package com.triviagame.results;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import com.triviagame.db.PlayerDao;
import com.triviagame.db.ScoreDao;

public class Results {

    private final ScoreDao scoreDao;
    private final PlayerDao playerDao;

    public Results() {
        scoreDao = new ScoreDao();
        playerDao = new PlayerDao();
    }

    public void show(Scanner scanner) throws SQLException {

        boolean running = true;

        while (running) {

            System.out.println("\n===== GAME RESULTS =====");
            System.out.println("1. View Leaderboard");
            System.out.println("2. View Player Results");
            System.out.println("3. Back to Home");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1":
                    showLeaderboard();
                    break;

                case "2":
                    showPlayerResults(scanner);
                    break;

                case "3":
                    running = false;
                    break;

                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    public void showLeaderboard() throws SQLException {

        List<PlayerDao.PlayerRecord> players =
                playerDao.getAllPlayers();

        if (players.isEmpty()) {
            System.out.println("No players available.");
            return;
        }

        record PlayerScore(
                String name,
                long points
        ) {}

        List<PlayerScore> leaderboard =
                new java.util.ArrayList<>();

        for (PlayerDao.PlayerRecord player : players) {

            long points = scoreDao.getTotalPointsByPlayer(
                    player.id()
            );

            leaderboard.add(
                new PlayerScore(player.name(), points)
            );
        }

        leaderboard.sort(
            Comparator.comparingLong(
                PlayerScore::points
            ).reversed()
        );

        System.out.println("\n===== LEADERBOARD =====");

        int rank = 1;

        for (PlayerScore player : leaderboard) {

            System.out.println(
                rank + ". " + player.name()
                + " - " + player.points() + " points"
            );

            rank++;
        }
    }

    private void showPlayerResults(Scanner scanner)
            throws SQLException {

        System.out.print("Enter Player ID: ");

        int playerId;

        try {
            playerId = Integer.parseInt(
                scanner.nextLine().trim()
            );
            
        } catch (NumberFormatException e) {
            
            System.out.println("Invalid player ID.");
            return;
        }

        var player = playerDao.getPlayerById(playerId);

        if (player.isEmpty()) {
            System.out.println("Player not found.");
            return;
        }

        List<ScoreDao.ScoreRecord> scores = scoreDao.getScoresByPlayer(playerId);

        System.out.println(
            "\nResults for: " + player.get().name()
        );

        if (scores.isEmpty()) {
            System.out.println("No game results yet.");
            return;
        }

        for (ScoreDao.ScoreRecord result : scores) {

            System.out.println("------------------");

            System.out.println(
                "Session ID: " + result.sessionId()
            );

            System.out.println(
                "Question ID: " + result.questionId()
            );

            System.out.println(
                "Correct: " + result.isCorrect()
            );

            System.out.println(
                "Points: " + result.points()
            );
        }

        System.out.println("------------------");

        long totalPoints =
                scoreDao.getTotalPointsByPlayer(playerId);

        System.out.println(
            "Total Points: " + totalPoints
        );
    }
}
