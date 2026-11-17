package com.triviagame.results;

import com.triviagame.home.Ui;
import com.triviagame.service.LeaderboardService;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;

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
    private final LeaderboardService service = new LeaderboardService();
    private final ExecutorService workers;
    private long requestVersion;
    private final TableView<Row> table = new TableView<>();
    private final Label summary = Ui.label("", "card-note");

    public Results(ExecutorService workers) {
        this.workers = workers;
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
        long version = ++requestVersion;
        Task<List<LeaderboardService.Entry>> task = new Task<>() {
            @Override protected List<LeaderboardService.Entry> call() throws Exception { return service.leaderboard(); }
        };
        task.setOnSucceeded(event -> {
            if (version != requestVersion) return;
            var rows = task.getValue().stream().map(row -> new Row(row.rank(), row.player(), row.points(), row.average())).toList();
            table.setItems(FXCollections.observableArrayList(rows));
            summary.setText(rows.size() + " players ranked");
        });
        task.setOnFailed(event -> {
            if (version != requestVersion) return;
            Logger.getLogger(Results.class.getName()).log(Level.WARNING, "Leaderboard query failed", task.getException());
            Ui.error("Unable to load results. Please try again.");
        });
        workers.execute(task);
    }
}
