package com.triviagame.service;

import com.triviagame.dao.CategoryDao;
import com.triviagame.dao.PlayerDao;
import com.triviagame.dao.QuestionDao;
import com.triviagame.dao.ScoreDao;
import java.sql.SQLException;

/** Fetches aggregate dashboard counts without materializing database tables. */
public final class DashboardService {
  public record Counts(int players, int games, int questions, int categories) {}

  public Counts counts() throws SQLException {
    return new Counts(
        new PlayerDao().countPlayers(),
        new ScoreDao().countPlayedSessions(),
        new QuestionDao().countQuestions(),
        new CategoryDao().countCategories());
  }
}
