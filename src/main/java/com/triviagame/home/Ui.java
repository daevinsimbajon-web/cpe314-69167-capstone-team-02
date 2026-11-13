package com.triviagame.home;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/**
 * Small helpers shared by every screen, so each screen stays short
 * and all styling comes from style.css.
 */
public final class Ui {

    private Ui() {
    }

    // A label that gets its look from a style.css class.
    public static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    // A button that gets its look from a style.css class.
    public static Button button(
            String text,
            String styleClass,
            EventHandler<ActionEvent> action
    ) {
        Button button = new Button(text);
        button.getStyleClass().add(styleClass);
        button.setOnAction(action);
        return button;
    }

    // Empty space that pushes the next item to the far side of an HBox.
    public static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static void error(String message) {
        show(Alert.AlertType.ERROR, "Something went wrong", message);
    }

    public static void warning(String message) {
        show(Alert.AlertType.WARNING, "Please check", message);
    }

        // Asks a yes/no question. Returns true only if the user picks the
    // confirm button; closing the dialog counts as "no".
    public static boolean confirm(
            String header,
            String message,
            String confirmText,
            String cancelText
    ) {
        ButtonType yes = new ButtonType(
                confirmText,
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType no = new ButtonType(
                cancelText,
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, yes, no);

        alert.setTitle("Trivia Night");
        alert.setHeaderText(header);

        // Apply the application's dark theme to the dialog.
        var stylesheet = Ui.class.getResource("/style.css");

        if (stylesheet != null) {
            alert.getDialogPane().getStylesheets()
                    .add(stylesheet.toExternalForm());
        }

        return alert.showAndWait().orElse(no) == yes;
    }
    private static void show(
            Alert.AlertType type,
            String header,
            String message
    ) {
        Alert alert = new Alert(type);
        alert.setTitle("Trivia Night");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
