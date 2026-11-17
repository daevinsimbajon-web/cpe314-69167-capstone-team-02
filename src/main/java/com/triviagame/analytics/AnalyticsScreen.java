package com.triviagame.analytics;

import com.triviagame.db.AnalyticsDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.home.Ui;

import java.sql.SQLException;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Charts built from the database. Nothing here is hard-coded:
 * press Refresh (or pick another player) and both charts are
 * rebuilt from the latest rows.
 */
public class AnalyticsScreen {

    private final VBox root = new VBox();

    private final AnalyticsDao analyticsDao = new AnalyticsDao();
    private final PlayerDao playerDao = new PlayerDao();

    // Bar chart: accuracy per category
    private final CategoryAxis accuracyX = new CategoryAxis();
    private final NumberAxis accuracyY = new NumberAxis(0, 100, 20);
    private final BarChart<String, Number> accuracyChart =
            new BarChart<>(accuracyX, accuracyY);
    private final Label accuracyEmpty = Ui.label(
            "No answers recorded yet. Play a game or import scores.",
            "muted"
    );

    // Line chart: points per game for one player
    private final CategoryAxis sessionX = new CategoryAxis();
    private final NumberAxis sessionY = new NumberAxis();
    private final LineChart<String, Number> sessionChart =
            new LineChart<>(sessionX, sessionY);
    private final Label sessionEmpty = Ui.label(
            "This player has no saved games yet.",
            "muted"
    );

    private final ComboBox<PlayerDao.PlayerRecord> playerBox =
            new ComboBox<>();

    public AnalyticsScreen() {
        root.getStyleClass().add("screen");

        setUpCharts();
        setUpPlayerBox();

        HBox header = new HBox(
                15,
                new VBox(5,
                        Ui.label("Analytics", "title"),
                        Ui.label("Charts built from your saved games.",
                                "subtitle")
                ),
                Ui.spacer(),
                Ui.button("↻  Refresh", "secondary-button",
                        event -> refresh())
        );
        header.setAlignment(Pos.CENTER_LEFT);

        VBox accuracyCard = new VBox(
                Ui.label("Accuracy by category", "card-title"),
                accuracyChart,
                accuracyEmpty
        );
        accuracyCard.getStyleClass().add("card");

        HBox sessionHeader = new HBox(
                15,
                Ui.label("Points per game", "card-title"),
                Ui.spacer(),
                playerBox
        );
        sessionHeader.setAlignment(Pos.CENTER_LEFT);

        VBox sessionCard = new VBox(
                sessionHeader,
                sessionChart,
                sessionEmpty
        );
        sessionCard.getStyleClass().add("card");

        HBox charts = new HBox(20, accuracyCard, sessionCard);
        HBox.setHgrow(accuracyCard, Priority.ALWAYS);
        HBox.setHgrow(sessionCard, Priority.ALWAYS);
        accuracyCard.setPrefWidth(600);
        sessionCard.setPrefWidth(600);
        VBox.setVgrow(charts, Priority.ALWAYS);

        root.getChildren().addAll(header, charts);

        refresh();
    }

    public Parent getView() {
        return root;
    }

    private void setUpCharts() {
        accuracyChart.setTitle("Correct answers per category");
        accuracyX.setLabel("Category");
        accuracyY.setLabel("Accuracy (%)");
        accuracyChart.setLegendVisible(true);
        accuracyChart.setAnimated(false);
        accuracyChart.setPrefHeight(420);
        VBox.setVgrow(accuracyChart, Priority.ALWAYS);

        sessionChart.setTitle("Points earned in each game");
        sessionX.setLabel("Game (date played)");
        sessionY.setLabel("Points");
        sessionChart.setLegendVisible(true);
        sessionChart.setAnimated(false);
        sessionChart.setPrefHeight(420);
        VBox.setVgrow(sessionChart, Priority.ALWAYS);
    }

    private void setUpPlayerBox() {
        playerBox.setPromptText("Choose a player");
        playerBox.setConverter(
                new StringConverter<PlayerDao.PlayerRecord>() {
                    @Override
                    public String toString(PlayerDao.PlayerRecord player) {
                        return player == null ? "" : player.name();
                    }

                    @Override
                    public PlayerDao.PlayerRecord fromString(String text) {
                        return null;
                    }
                }
        );

        playerBox.valueProperty().addListener(
                (observable, oldValue, newValue) -> loadSessions()
        );
    }

    // Re-read everything from the database
    private void refresh() {
        try {
            PlayerDao.PlayerRecord previous = playerBox.getValue();

            List<PlayerDao.PlayerRecord> players =
                    playerDao.getAllPlayers();

            playerBox.setItems(
                    FXCollections.observableArrayList(players)
            );

            // Keep the same player selected after a refresh
            PlayerDao.PlayerRecord toSelect = null;

            if (previous != null) {
                for (PlayerDao.PlayerRecord player : players) {
                    if (player.id() == previous.id()) {
                        toSelect = player;
                    }
                }
            }

            if (toSelect != null) {
                playerBox.getSelectionModel().select(toSelect);
            } else if (!players.isEmpty()) {
                playerBox.getSelectionModel().selectFirst();
            }

            loadAccuracy();
            loadSessions();

        } catch (SQLException e) {
            Ui.error("Unable to load analytics: " + e.getMessage());
        }
    }

    private void loadAccuracy() {
        try {
            List<AnalyticsDao.CategoryAccuracy> rows =
                    analyticsDao.getAccuracyByCategory();

            XYChart.Series<String, Number> series =
                    new XYChart.Series<>();
            series.setName("Accuracy %");

            for (AnalyticsDao.CategoryAccuracy row : rows) {
                series.getData().add(
                        new XYChart.Data<String, Number>(
                                row.categoryName(),
                                row.accuracyPercent()
                        )
                );
            }

            accuracyChart.getData().clear();
            accuracyChart.getData().add(series);

            setShown(accuracyEmpty, rows.isEmpty());

        } catch (SQLException e) {
            Ui.error("Unable to load accuracy chart: " + e.getMessage());
        }
    }

    private void loadSessions() {
        PlayerDao.PlayerRecord player = playerBox.getValue();

        sessionChart.getData().clear();

        if (player == null) {
            setShown(sessionEmpty, true);
            return;
        }

        try {
            List<AnalyticsDao.SessionPoints> rows =
                    analyticsDao.getPointsPerSession(player.id());

            XYChart.Series<String, Number> series =
                    new XYChart.Series<>();
            series.setName(player.name());

            for (int i = 0; i < rows.size(); i++) {
                AnalyticsDao.SessionPoints row = rows.get(i);

                String played = row.playedAt() == null
                        ? ""
                        : row.playedAt().substring(
                                0, Math.min(10, row.playedAt().length()));

                // The number makes every label unique, even when two
                // games were played on the same day.
                series.getData().add(
                        new XYChart.Data<String, Number>(
                                "Game " + (i + 1) + " (" + played + ")",
                                row.points()
                        )
                );
            }

            sessionChart.getData().add(series);

            setShown(sessionEmpty, rows.isEmpty());

        } catch (SQLException e) {
            Ui.error("Unable to load points chart: " + e.getMessage());
        }
    }

    // Show or hide the "no data" message completely
    private void setShown(Label label, boolean shown) {
        label.setVisible(shown);
        label.setManaged(shown);
    }
}
