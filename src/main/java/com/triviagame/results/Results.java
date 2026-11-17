package com.triviagame.results;

import com.triviagame.db.AnalyticsDao;
import com.triviagame.home.Ui;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Leaderboard screen. This replaces the old console-based Results
 * class, which used Scanner and System.out and could not be shown
 * in the JavaFX window.
 */
public class Results {

    // One row of the leaderboard
    public record Row(
            int rank,
            String player,
            long totalPoints,
            double averagePoints
    ) {
    }

    private final VBox root = new VBox();
    private final AnalyticsDao analyticsDao = new AnalyticsDao();
    private final TableView<Row> table = new TableView<>();
    private final Label summary = Ui.label("", "card-note");

    public Results() {
        root.getStyleClass().add("screen");

        VBox card = createCard();
        VBox.setVgrow(card, Priority.ALWAYS);

        root.getChildren().addAll(
                Ui.label("Results", "title"),
                Ui.label("Leaderboard across all saved games.", "subtitle"),
                card
        );

        loadLeaderboard();
    }

    public Parent getView() {
        return root;
    }

    private VBox createCard() {
        VBox card = new VBox();
        card.getStyleClass().add("card");

        VBox heading = new VBox(5,
                Ui.label("Leaderboard", "card-title"),
                summary
        );

        HBox header = new HBox(
                15,
                heading,
                Ui.spacer(),
                Ui.button("↻  Refresh", "secondary-button",
                        event -> loadLeaderboard())
        );
        header.setAlignment(Pos.CENTER_LEFT);

        TableColumn<Row, Number> rankColumn = new TableColumn<>("Rank");
        rankColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().rank())
        );

        TableColumn<Row, String> playerColumn = new TableColumn<>("Player");
        playerColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().player())
        );

        TableColumn<Row, Number> totalColumn =
                new TableColumn<>("Total points");
        totalColumn.setCellValueFactory(data ->
                new SimpleLongProperty(data.getValue().totalPoints())
        );

        TableColumn<Row, String> averageColumn =
                new TableColumn<>("Average per answer");
        averageColumn.setCellValueFactory(data ->
                new SimpleStringProperty(
                        String.format("%.1f", data.getValue().averagePoints())
                )
        );

        table.getColumns().add(rankColumn);
        table.getColumns().add(playerColumn);
        table.getColumns().add(totalColumn);
        table.getColumns().add(averageColumn);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );
        table.setPlaceholder(new Label(
                "No scores yet. Play a game or import score history."
        ));
        VBox.setVgrow(table, Priority.ALWAYS);

        card.getChildren().addAll(header, table);
        return card;
    }

    // Reads the database again every time, so new games show up
    private void loadLeaderboard() {
        try {
            List<AnalyticsDao.PlayerTotal> totals =
                    analyticsDao.getTopPlayers(100);

            Map<String, Double> averages = new HashMap<>();

            for (AnalyticsDao.PlayerAverage average
                    : analyticsDao.getAveragePointsPerPlayer()) {

                averages.putIfAbsent(
                        average.playerName(),
                        average.averagePoints()
                );
            }

            List<Row> rows = new ArrayList<>();
            int rank = 0;
            long previousTotal = -1;

            for (int i = 0; i < totals.size(); i++) {
                AnalyticsDao.PlayerTotal total = totals.get(i);

                // Players with the same points share a rank
                if (total.totalPoints() != previousTotal) {
                    rank = i + 1;
                    previousTotal = total.totalPoints();
                }

                rows.add(new Row(
                        rank,
                        total.playerName(),
                        total.totalPoints(),
                        averages.getOrDefault(total.playerName(), 0.0)
                ));
            }

            table.setItems(FXCollections.observableArrayList(rows));

            summary.setText(rows.size() + " players ranked");

        } catch (SQLException e) {
            Ui.error("Unable to load results: " + e.getMessage());
        }
    }
}
