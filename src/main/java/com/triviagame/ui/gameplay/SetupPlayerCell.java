package com.triviagame.ui.gameplay;

import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;

/** Displays each selected player with the game setup colors. */
final class SetupPlayerCell extends ListCell<String> {
  SetupPlayerCell() {
    selectedProperty().addListener((observable, previous, selected) -> updateStyle());
  }

  @Override
  protected void updateItem(String player, boolean empty) {
    super.updateItem(player, empty);
    setText(empty ? null : player);
    setTextFill(Color.WHITE);
    updateStyle();
  }

  private void updateStyle() {
    String background = isSelected() ? "#8B5CF6" : "#232540";
    setStyle("-fx-background-color: " + background + "; -fx-padding: 11;");
  }
}
