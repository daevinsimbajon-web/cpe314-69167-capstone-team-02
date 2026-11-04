package com.triviagame.home;

import javafx.scene.Node;

/**
 * How a screen asks the main window to change what is shown.
 * Home implements it, so screens never touch the window directly.
 */
public interface Navigator {

    // Replace the center of the window with this screen.
    void show(Node screen);

    // Go to the New Game setup screen.
    void openNewGame();

    // Go to the Results (leaderboard) screen.
    void openResults();
}
