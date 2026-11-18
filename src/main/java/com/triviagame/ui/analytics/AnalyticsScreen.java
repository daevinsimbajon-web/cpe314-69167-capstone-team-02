package com.triviagame.ui.analytics;

import com.triviagame.controller.AnalyticsController;
import com.triviagame.model.AnalyticsRows;
import com.triviagame.model.PlayerRecord;
import com.triviagame.ui.home.Ui;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 * Charts built from the database. Nothing here is hard-coded: press Refresh (or pick another
 * player) and both charts are rebuilt from the latest rows.
 */
public class AnalyticsScreen {

  private final VBox root = new VBox();

  private final AnalyticsController controller;
  private final ExecutorService workers;
  private long requestVersion;
  private boolean updatingSelection;
  private List<AnalyticsRows.BestCategory> bestCategories = List.of();
  private final Label bestCategory = Ui.label("", "muted");

  // Bar chart: accuracy per category
  private final CategoryAxis accuracyX = new CategoryAxis();
  private final NumberAxis accuracyY = new NumberAxis(0, 100, 20);
  private final BarChart<String, Number> accuracyChart = new BarChart<>(accuracyX, accuracyY);
  private final Label accuracyEmpty =
      Ui.label("No answers recorded yet. Play a game or import scores.", "muted");

  // Line chart: points per game for one player
  private final CategoryAxis sessionX = new CategoryAxis();
  private final NumberAxis sessionY = new NumberAxis();
  private final LineChart<String, Number> sessionChart = new LineChart<>(sessionX, sessionY);
  private final Label sessionEmpty = Ui.label("This player has no saved games yet.", "muted");

  private final ComboBox<PlayerRecord> playerBox = new ComboBox<>();

  public AnalyticsScreen(AnalyticsController controller, ExecutorService workers) {
    this.controller = controller;
    this.workers = workers;
    root.getStyleClass().add("screen");

    setUpCharts();
    setUpPlayerBox();

    HBox header =
        new HBox(
            15,
            new VBox(
                5,
                Ui.label("Analytics", "title"),
                Ui.label("Charts built from your saved games.", "subtitle")),
            Ui.spacer(),
            Ui.button("↻  Refresh", "secondary-button", event -> refresh()));
    header.setAlignment(Pos.CENTER_LEFT);

    VBox accuracyCard =
        new VBox(Ui.label("Accuracy by category", "card-title"), accuracyChart, accuracyEmpty);
    accuracyCard.getStyleClass().add("card");

    HBox sessionHeader =
        new HBox(15, Ui.label("Points per game", "card-title"), Ui.spacer(), playerBox);
    sessionHeader.setAlignment(Pos.CENTER_LEFT);

    VBox sessionCard = new VBox(sessionHeader, bestCategory, sessionChart, sessionEmpty);
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

    sessionChart.setTitle("Points and average across games");
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
        new StringConverter<PlayerRecord>() {
          @Override
          public String toString(PlayerRecord player) {
            return player == null ? "" : player.name();
          }

          @Override
          public PlayerRecord fromString(String text) {
            return null;
          }
        });

    playerBox
        .valueProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (!updatingSelection) loadSessions();
            });
  }

  // Re-read everything from the database
  private void refresh() {
    var previous = playerBox.getValue();
    Integer selectedId = previous == null ? null : previous.id();
    long version = ++requestVersion;
    var task = controller.load(selectedId);
    task.setOnSucceeded(
        event -> {
          if (version != requestVersion) return;
          var snapshot = task.getValue();
          bestCategories = snapshot.bestCategories();
          updatingSelection = true;
          try {
            playerBox.setItems(FXCollections.observableArrayList(snapshot.players()));
            playerBox.getSelectionModel().select(snapshot.selected());
          } finally {
            updatingSelection = false;
          }
          showAccuracy(snapshot.accuracy());
          showSessions(snapshot.selected(), snapshot.sessions());
        });
    task.setOnFailed(event -> showError(version, task.getException()));
    workers.execute(task);
  }

  private void showAccuracy(List<AnalyticsRows.CategoryAccuracy> rows) {
    XYChart.Series<String, Number> series = new XYChart.Series<>();
    series.setName("Accuracy %");

    for (AnalyticsRows.CategoryAccuracy row : rows) {
      series
          .getData()
          .add(new XYChart.Data<String, Number>(row.categoryName(), row.accuracyPercent()));
    }

    accuracyChart.getData().clear();
    accuracyChart.getData().add(series);

    setShown(accuracyEmpty, rows.isEmpty());
  }

  private void loadSessions() {
    PlayerRecord player = playerBox.getValue();

    long version = ++requestVersion;
    if (player == null) {
      showSessions(null, List.of());
      return;
    }
    var task = controller.sessions(player.id());
    task.setOnSucceeded(
        event -> {
          if (version == requestVersion) showSessions(player, task.getValue());
        });
    task.setOnFailed(event -> showError(version, task.getException()));
    workers.execute(task);
  }

  private void showSessions(PlayerRecord player, List<AnalyticsRows.SessionPoints> rows) {
    sessionChart.getData().clear();
    if (player == null) {
      bestCategory.setText("");
      setShown(sessionEmpty, true);
      return;
    }
    var best = bestCategories.stream().filter(item -> item.playerId() == player.id()).findFirst();
    bestCategory.setText(
        best.map(item -> "Best category: " + item.categoryName())
            .orElse("Best category: no answers yet"));
    XYChart.Series<String, Number> series = new XYChart.Series<>();
    series.setName(player.name());
    XYChart.Series<String, Number> average = new XYChart.Series<>();
    average.setName("Average so far");
    long total = 0;

    for (int i = 0; i < rows.size(); i++) {
      AnalyticsRows.SessionPoints row = rows.get(i);
      total += row.points();

      String played =
          row.playedAt() == null
              ? ""
              : row.playedAt().substring(0, Math.min(10, row.playedAt().length()));

      // The number makes every label unique, even when two
      // games were played on the same day.
      series
          .getData()
          .add(
              new XYChart.Data<String, Number>(
                  "Game " + (i + 1) + " (" + played + ")", row.points()));
      average
          .getData()
          .add(
              new XYChart.Data<>(
                  "Game " + (i + 1) + " (" + played + ")", (double) total / (i + 1)));
    }

    sessionChart.getData().add(series);
    sessionChart.getData().add(average);

    setShown(sessionEmpty, rows.isEmpty());
  }

  private void showError(long version, Throwable error) {
    if (version != requestVersion) return;
    Logger.getLogger(AnalyticsScreen.class.getName())
        .log(Level.WARNING, "Analytics query failed", error);
    Ui.error("Unable to load analytics. Please try again.");
  }

  // Show or hide the "no data" message completely
  private void setShown(Label label, boolean shown) {
    label.setVisible(shown);
    label.setManaged(shown);
  }
}
