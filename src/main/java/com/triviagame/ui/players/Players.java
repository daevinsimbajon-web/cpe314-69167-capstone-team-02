package com.triviagame.ui.players;

import com.triviagame.controller.PlayerController;
import com.triviagame.model.PlayerRecord;
import com.triviagame.ui.home.Ui;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class Players {

  private final VBox root = new VBox();

  private final PlayerController controller;
  private final ExecutorService workers;
  private boolean busy;

  private final TableView<PlayerRecord> table = new TableView<>();

  private final Label totalPlayers = Ui.label("Total: 0 players", "card-note");

  public Players(PlayerController controller, ExecutorService workers) {
    this.controller = controller;
    this.workers = workers;
    root.getStyleClass().add("screen");

    VBox card = createPlayerCard();
    VBox.setVgrow(card, Priority.ALWAYS);

    root.getChildren()
        .addAll(
            Ui.label("Players", "title"),
            Ui.label("Manage your registered trivia players.", "subtitle"),
            card);

    loadPlayers();
  }

  public Parent getView() {
    return root;
  }

  private VBox createPlayerCard() {
    VBox card = new VBox();
    card.getStyleClass().add("card");

    VBox heading = new VBox(5, Ui.label("Registered Players", "card-title"), totalPlayers);

    Button addButton = Ui.button("+ Add Player", "primary-button", event -> showAddPlayerDialog());

    HBox header = new HBox(15, heading, Ui.spacer(), addButton);
    header.setAlignment(Pos.CENTER_LEFT);

    TableColumn<PlayerRecord, Number> idColumn = new TableColumn<>("ID");
    idColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().id()));

    TableColumn<PlayerRecord, String> nameColumn = new TableColumn<>("Player Name");
    nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));

    idColumn.setPrefWidth(100);
    nameColumn.setPrefWidth(500);

    table.getColumns().add(idColumn);
    table.getColumns().add(nameColumn);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    table.setPlaceholder(new Label("No players registered yet."));
    VBox.setVgrow(table, Priority.ALWAYS);

    Button deleteButton =
        Ui.button("Delete Selected Player", "danger-button", event -> deleteSelectedPlayer());

    card.getChildren().addAll(header, table, deleteButton);
    return card;
  }

  private void loadPlayers() {
    if (busy) return;
    busy = true;
    var task = controller.list();
    task.setOnSucceeded(
        event -> {
          busy = false;
          var players = task.getValue();
          table.setItems(FXCollections.observableArrayList(players));
          totalPlayers.setText("Total: " + players.size() + " players");
        });
    task.setOnFailed(event -> failure("Unable to load players.", task.getException()));
    workers.execute(task);
  }

  private void showAddPlayerDialog() {
    TextInputDialog dialog = new TextInputDialog();

    dialog.setTitle("Add Player");
    dialog.setHeaderText("Register a New Player");
    dialog.setContentText("Player name:");

    dialog
        .showAndWait()
        .ifPresent(
            name -> {
              if (name.isBlank()) {
                Ui.warning("Player name cannot be empty.");
                return;
              }

              if (busy) return;
              busy = true;
              var task = controller.add(name);
              task.setOnSucceeded(
                  event -> {
                    busy = false;
                    loadPlayers();
                  });
              task.setOnFailed(event -> failure("Unable to add player.", task.getException()));
              workers.execute(task);
            });
  }

  private void deleteSelectedPlayer() {
    PlayerRecord selected = table.getSelectionModel().getSelectedItem();

    if (selected == null) {
      Ui.warning("Please select a player first.");
      return;
    }

    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
    confirmation.setTitle("Delete Player");
    confirmation.setHeaderText("Delete " + selected.name() + "?");
    confirmation.setContentText("Are you sure you want to delete this player?");

    confirmation
        .showAndWait()
        .ifPresent(
            response -> {
              if (response == ButtonType.OK) {
                if (busy) return;
                busy = true;
                var task = controller.deleteIfUnscored(selected.id());
                task.setOnSucceeded(
                    event -> {
                      busy = false;
                      if (!task.getValue())
                        Ui.warning(
                            selected.name()
                                + " has saved game scores, so this"
                                + " player cannot be deleted.");
                      else loadPlayers();
                    });
                task.setOnFailed(event -> failure("Unable to delete player.", task.getException()));
                workers.execute(task);
              }
            });
  }

  private void failure(String message, Throwable error) {
    busy = false;
    Logger.getLogger(Players.class.getName()).log(Level.WARNING, message, error);
    Ui.error(message + " Please try again.");
  }
}
