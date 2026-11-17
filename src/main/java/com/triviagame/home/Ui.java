package com.triviagame.home;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

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
