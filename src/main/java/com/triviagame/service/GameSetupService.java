package com.triviagame.service;

import com.triviagame.dao.CategoryDao;
import com.triviagame.dao.PlayerDao;
import com.triviagame.dao.QuestionDao;
import com.triviagame.dao.SqliteGamePersistence;
import com.triviagame.gameplay.GameSession;
import com.triviagame.model.CategoryRecord;
import com.triviagame.model.GamePlayer;
import com.triviagame.model.PlayerRecord;
import com.triviagame.model.questions.Question;
import com.triviagame.model.questions.QuestionMapper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Reads and prepares game data on a desktop worker. */
public final class GameSetupService {
  public record Setup(List<CategoryRecord> categories, List<PlayerRecord> players) {}

  public record Filters(int categoryId, String difficulty, String type) {}

  private final CategoryDao categories = new CategoryDao();
  private final QuestionDao questions = new QuestionDao();
  private final PlayerDao players = new PlayerDao();
  private final SqliteGamePersistence persistence = new SqliteGamePersistence();

  public Setup setup() throws SQLException {
    return new Setup(categories.getAllCategories(), players.getAllPlayers());
  }

  public int count(Filters filters) throws SQLException {
    return questions.countMatchingQuestions(
        filters.categoryId(), filters.difficulty(), filters.type());
  }

  public List<QuestionDao.QuestionRecord> matching(Filters filters) throws SQLException {
    var pool =
        filters.categoryId() == 0
            ? questions.getAllQuestions()
            : categories.getQuestionsRecursively(filters.categoryId());
    return pool.stream()
        .filter(
            q ->
                filters.difficulty().equalsIgnoreCase("any")
                    || q.difficulty().equalsIgnoreCase(filters.difficulty()))
        .filter(q -> filters.type().equals("ANY") || filters.type().equals(q.questionType()))
        .toList();
  }

  /** Resolves players and questions before any session row is created. */
  public GameSession prepareGame(
      List<GamePlayer.Setup> selectedPlayers,
      List<QuestionDao.QuestionRecord> bank,
      int questionsPerPlayer,
      LongSupplier clock)
      throws SQLException {
    if (bank == null || questionsPerPlayer < 1 || questionsPerPlayer > bank.size()) {
      throw new IllegalArgumentException("Not enough bank questions for requested game length");
    }
    if (selectedPlayers == null || selectedPlayers.isEmpty()) {
      throw new IllegalArgumentException("Add at least one player");
    }
    var pool = new ArrayList<>(bank);
    Collections.shuffle(pool);
    var categoryById = new HashMap<Integer, CategoryRecord>();
    for (var category : categories.getAllCategories()) {
      categoryById.put(category.id(), category);
    }
    var selected = pool.subList(0, questionsPerPlayer);
    List<List<Question>> prepared = new ArrayList<>();
    for (int index = 0; index < selectedPlayers.size(); index++) {
      // The same questions are used every round, but each player has shuffled choices.
      prepared.add(
          selected.stream()
              .map(
                  record ->
                      QuestionMapper.toQuestion(
                          record, ancestry(record.categoryId(), categoryById)))
              .toList());
    }
    return new GameSession(
        persistence.preparePlayers(selectedPlayers), prepared, persistence, clock);
  }

  private static List<String> ancestry(int id, Map<Integer, CategoryRecord> byId) {
    var names = new ArrayList<String>();
    var visited = new HashSet<Integer>();
    Integer current = id;
    while (current != null && visited.add(current)) {
      var category = byId.get(current);
      if (category == null) {
        break;
      }
      names.add(category.name());
      current = category.parentId();
    }
    return names;
  }
}
