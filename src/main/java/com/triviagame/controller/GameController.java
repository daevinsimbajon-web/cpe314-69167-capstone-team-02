package com.triviagame.controller;

import com.triviagame.gameplay.GameSession;
import com.triviagame.model.GamePlayer;
import com.triviagame.service.GameSetupService;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Coordinates game setup and starts the domain model from saved questions. */
public final class GameController {
  public record GameStart(int availableQuestions, Optional<GameSession> game) {}

  private final GameSetupService setupService;

  public GameController() {
    this(new GameSetupService());
  }

  GameController(GameSetupService setupService) {
    this.setupService = setupService;
  }

  public GameSetupService.Setup setup() throws SQLException {
    return setupService.setup();
  }

  public int count(GameSetupService.Filters filters) throws SQLException {
    return setupService.count(filters);
  }

  public GameStart startGame(
      List<GamePlayer.Setup> players, GameSetupService.Filters filters, int questionsPerPlayer)
      throws SQLException {
    var questions = setupService.matching(filters);
    if (questions.size() < questionsPerPlayer) {
      return new GameStart(questions.size(), Optional.empty());
    }
    return new GameStart(
        questions.size(),
        Optional.of(
            setupService.prepareGame(players, questions, questionsPerPlayer, System::nanoTime)));
  }
}
