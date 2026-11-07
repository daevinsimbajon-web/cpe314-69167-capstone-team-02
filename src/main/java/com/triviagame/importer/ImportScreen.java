package com.triviagame.importer;
import com.triviagame.db.ScoreJsonExporter;
import com.triviagame.db.ScoreJsonImporter;
import com.triviagame.home.Ui;
import java.io.File;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
/**
 * Imports and exports score history in JSON format.
 * Newly exported files have stable IDs and can be imported repeatedly
 * without adding duplicate scores.
 */
public final class ImportScreen {
 private final VBox root = new VBox();
 private final ScoreJsonImporter importer;
 private final Label fileLabel =
 Ui.label("No import file selected", "muted");
 private final Button chooseButton = Ui.button(
 "Choose JSON File", "secondary-button",
 event -> chooseFile());
 private final Button importButton = Ui.button(
 "Import Scores", "primary-button",
 event -> runImport());
 private final Button exportButton = Ui.button(
 "Export Scores to JSON", "secondary-button",
 event -> runExport());
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
 details.setPromptText(
 "Your export path or import report will appear here.");
 VBox.setVgrow(details, Priority.ALWAYS);
 HBox fileRow = new HBox(15, chooseButton, fileLabel);
 fileRow.setAlignment(Pos.CENTER_LEFT);
 HBox actionRow = new HBox(
 15, importButton, exportButton, progress);
 actionRow.setAlignment(Pos.CENTER_LEFT);
 Label explanation = Ui.label(
 "Export your saved scores as a JSON file. "
 + "You can import that file on another computer.",
 "muted");
 explanation.setWrapText(true);
 Label note = Ui.label(
 "Safe sync: newly exported JSON files include permanent "
 + "score IDs. Importing the same file again skips duplicate "
 + "scores; it never overwrites them. New exports also include the question bank information "
 + "needed to restore missing questions and categories. "
 + "Older JSON files without IDs can still create duplicates.",
 "muted");
 note.setWrapText(true);
 VBox card = new VBox(15);
 card.getStyleClass().add("card");
 VBox.setVgrow(card, Priority.ALWAYS);
 card.getChildren().addAll(
 Ui.label("Import and Export Scores", "card-title"),
 explanation,
 note,
 fileRow,
 actionRow,
 status,
 details);
 root.getChildren().addAll(
 Ui.label("Import / Export Scores", "title"),
 Ui.label("Move game results between computers safely.",
 "subtitle"),
 card);
 }
 public Parent getView() {
 return root;
 }
 private FileChooser chooser() {
 FileChooser picker = new FileChooser();
 picker.getExtensionFilters().add(
 new FileChooser.ExtensionFilter("JSON files", "*.json"));
 return picker;
 }
 private void chooseFile() {
 FileChooser picker = chooser();
 picker.setTitle("Choose score JSON file");
 File file = picker.showOpenDialog(root.getScene().getWindow());
 if (file != null) {
 selected = file.toPath();
 fileLabel.setText(file.getName());
 importButton.setDisable(false);
 status.setText("Ready to import selected file.");
 details.clear();
 }
 }
 private void runImport() {
 if (selected == null) {
 return;
 }
 Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
 confirm.setTitle("Import Scores");
 confirm.setHeaderText("Add scores from this JSON file?");
 confirm.setContentText(
 "New exported files are duplicate-safe: previously "
 + "imported scores will be skipped, not overwritten. "
 + "Old JSON files without IDs may still create duplicates. "
 + "New exports restore missing questions automatically. Continue?");
 if (confirm.showAndWait().orElse(ButtonType.CANCEL)
 != ButtonType.OK) {
 return;
 }
 setBusy(true);
 status.setText("Importing scores...");
 details.clear();
 importer.importAsync(selected).whenComplete((result, error) ->
 Platform.runLater(() -> {
 setBusy(false);
 if (error != null) {
 status.setText("Import failed.");
 details.setText(String.valueOf(error));
 return;
 }
 int invalid = Math.max(
 0, result.skipped() - result.duplicates());
 status.setText(
 "Added " + result.imported()
 + " | Duplicates " + result.duplicates()
 + " | Invalid " + invalid);
 String summary =
 "New scores added: " + result.imported()
 + "\nAlready imported (skipped): "
 + result.duplicates()
 + "\nInvalid entries (skipped): " + invalid;
 details.setText(result.errors().isEmpty()
 ? summary + "\n\nImport completed."
 : summary + "\n\n"
 + String.join("\n", result.errors()));
 }));
 }
 private void runExport() {
 FileChooser picker = chooser();
 picker.setTitle("Export Scores to JSON");
 picker.setInitialFileName("trivia_scores.json");
 File file = picker.showSaveDialog(root.getScene().getWindow());
 if (file == null) {
 return;
 }
 setBusy(true);
 status.setText("Exporting scores...");
 details.clear();
 Task<Integer> task = new Task<>() {
 @Override
 protected Integer call() throws Exception {
 return new ScoreJsonExporter().exportFile(file.toPath());
 }
 };
 task.setOnSucceeded(event -> {
 setBusy(false);
 status.setText("Exported " + task.getValue() + " scores.");
 details.setText(
 "Saved to: " + file.getAbsolutePath() + "\n\n"
 + "Send this file to the other PC. Missing categories and "
 + "questions will be restored during import. "
 + "Importing the same scores again will skip them.");
 });
 task.setOnFailed(event -> {
 setBusy(false);
 status.setText("Export failed.");
 Throwable exception = task.getException();
 details.setText(exception == null
 ? "Unknown export error"
 : exception.toString());
 });
 Thread worker = new Thread(task, "score-json-exporter");
 worker.setDaemon(true);
 worker.start();
 }
 private void setBusy(boolean busy) {
 progress.setVisible(busy);
 progress.setManaged(busy);
 chooseButton.setDisable(busy);
 importButton.setDisable(busy || selected == null);
 exportButton.setDisable(busy);
 }
}