package com.triviagame.importer;

import com.triviagame.db.ScoreJsonImporter;
import com.triviagame.home.Ui;

import java.io.File;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Imports score history from a JSON file.
 *
 * The import runs on the importer's own background thread
 * (ScoreJsonImporter.importAsync), so the window stays usable while it
 * works. The result comes back on that thread, so it is handed to the
 * JavaFX thread with Platform.runLater before any control is touched.
 */
public class ImportScreen {

    private final VBox root = new VBox();
    private final ScoreJsonImporter importer;

    private final Label fileLabel =
            Ui.label("No file selected", "muted");

    private final Button chooseButton = Ui.button(
            "Choose JSON file…", "secondary-button",
            event -> chooseFile()
    );

    private final Button importButton = Ui.button(
            "Import scores", "primary-button",
            event -> runImport()
    );

    private final ProgressIndicator progress = new ProgressIndicator();
    private final Label status = Ui.label("", "card-note");
    private final TextArea details = new TextArea();

    private Path selected;

    public ImportScreen(ScoreJsonImporter importer) {
        this.importer = importer;

        root.getStyleClass().add("screen");

        importButton.setDisable(true);

        progress.setPrefSize(28, 28);
        progress.setVisible(false);
        progress.setManaged(false);

        details.setEditable(false);
        details.setWrapText(true);
        details.setPromptText("Import results will appear here.");
        VBox.setVgrow(details, Priority.ALWAYS);

        HBox fileRow = new HBox(15, chooseButton, fileLabel);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(15, importButton, progress, status);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox();
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        Label format = Ui.label(
                "File format: a JSON array of objects with the fields "
                        + "player, category, questionText, isCorrect "
                        + "and points.",
                "muted"
        );
        format.setWrapText(true);

        card.getChildren().addAll(
                Ui.label("Import score history", "card-title"),
                format,
                fileRow,
                actionRow,
                details
        );

        root.getChildren().addAll(
                Ui.label("Import Scores", "title"),
                Ui.label("Load earlier game results from a JSON file.",
                        "subtitle"),
                card
        );
    }

    public Parent getView() {
        return root;
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a score history file");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON files", "*.json")
        );

        File file = chooser.showOpenDialog(root.getScene().getWindow());

        if (file != null) {
            selected = file.toPath();
            fileLabel.setText(file.getName());
            importButton.setDisable(false);
        }
    }

    private void runImport() {
        if (selected == null) {
            return;
        }

        setBusy(true);
        status.setText("Importing…");
        details.clear();

        // Runs on the importer's background thread. The window stays
        // responsive because nothing here blocks the JavaFX thread.
        importer.importAsync(selected).whenComplete((result, error) ->
                Platform.runLater(() -> {
                    setBusy(false);

                    if (error != null) {
                        status.setText("Import failed.");
                        details.setText(String.valueOf(error));
                        return;
                    }

                    status.setText(
                            "Imported " + result.imported()
                                    + " · Skipped " + result.skipped()
                    );

                    details.setText(
                            result.errors().isEmpty()
                                    ? "No problems found."
                                    : String.join("\n", result.errors())
                    );
                })
        );
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        progress.setManaged(busy);
        chooseButton.setDisable(busy);
        importButton.setDisable(busy || selected == null);
    }
}
