
package com.triviagame.players;

import com.triviagame.db.PlayerDao;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.sql.SQLException;

public class Players {

    private final VBox root = new VBox(22);

    private final String cardColor = "#1B1D30";
    private final String purple = "#8B5CF6";

    private final PlayerDao playerDao = new PlayerDao();
    private final TableView<PlayerDao.PlayerRecord> table =
            new TableView<>();

    private final Label totalPlayers = new Label("0");

    public Players() {
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: #10111F;");

        createHeader();
        createPlayerCard();
        loadPlayers();
    }

    public Parent getView() {
        return root;
    }

    private void createHeader() {
        Label title = new Label("Players");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: bold;");

        Label subtitle = new Label(
                "Manage your registered trivia players."
        );
        subtitle.setTextFill(Color.web("#A0A1B5"));

        root.getChildren().addAll(title, subtitle);
    }

    private void createPlayerCard() {
        VBox card = new VBox(18);
        card.setPadding(new Insets(25));
        card.setStyle(
                "-fx-background-color: " + cardColor +
                "; -fx-background-radius: 16;"
        );

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox heading = new VBox(5);

        Label sectionTitle = new Label("Registered Players");
        sectionTitle.setTextFill(Color.WHITE);
        sectionTitle.setStyle(
                "-fx-font-size: 19px; -fx-font-weight: bold;"
        );

        totalPlayers.setTextFill(Color.web("#A78BFA"));
        totalPlayers.setStyle("-fx-font-size: 13px;");

        heading.getChildren().addAll(sectionTitle, totalPlayers);

        Button addButton = new Button("+ Add Player");
        addButton.setStyle(
                "-fx-background-color: " + purple +
                "; -fx-text-fill: white;" +
                "-fx-background-radius: 9;" +
                "-fx-padding: 11 18;" +
                "-fx-font-weight: bold;"
        );
        addButton.setOnAction(event -> showAddPlayerDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(heading, spacer, addButton);

        TableColumn<PlayerDao.PlayerRecord, Number> idColumn =
                new TableColumn<>("ID");
        idColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        data.getValue().id()
                )
        );

        TableColumn<PlayerDao.PlayerRecord, String> nameColumn =
                new TableColumn<>("Player Name");
        nameColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().name()
                )
        );

        idColumn.setPrefWidth(100);
        nameColumn.setPrefWidth(500);

        table.getColumns().addAll(idColumn, nameColumn);
        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY
        );
        table.setPlaceholder(new Label("No players registered yet."));

        Button deleteButton = new Button("Delete Selected Player");
        deleteButton.setStyle(
                "-fx-background-color: #3B2432;" +
                "-fx-text-fill: #FCA5A5;" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 10 16;"
        );
        deleteButton.setOnAction(event -> deleteSelectedPlayer());

        card.getChildren().addAll(header, table, deleteButton);
        root.getChildren().add(card);
    }

    private void loadPlayers() {
        try {
            var players = playerDao.getAllPlayers();

            table.setItems(
                    FXCollections.observableArrayList(players)
            );

            totalPlayers.setText(
                    "Total: " + players.size() + " players"
            );

        } catch (SQLException e) {
            showError("Unable to load players: " + e.getMessage());
        }
    }

    private void showAddPlayerDialog() {
        TextInputDialog dialog = new TextInputDialog();

        dialog.setTitle("Add Player");
        dialog.setHeaderText("Register a New Player");
        dialog.setContentText("Player name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                showError("Player name cannot be empty.");
                return;
            }

            try {
                playerDao.addPlayer(name.trim());
                loadPlayers();
            } catch (SQLException e) {
                showError("Unable to add player: " + e.getMessage());
            }
        });
    }

    private void deleteSelectedPlayer() {
        PlayerDao.PlayerRecord selected =
                table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showError("Please select a player first.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Player");
        confirmation.setHeaderText("Delete " + selected.name() + "?");
        confirmation.setContentText(
                "Are you sure you want to delete this player?"
        );

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    playerDao.deletePlayer(selected.id());
                    loadPlayers();
                } catch (SQLException e) {
                    showError(
                            "Unable to delete player: " + e.getMessage()
                    );
                }
            }
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Players");
        alert.setHeaderText("Something went wrong");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
