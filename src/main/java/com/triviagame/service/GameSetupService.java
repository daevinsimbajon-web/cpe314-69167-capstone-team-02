package com.triviagame.service;

import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionDao;
import java.sql.SQLException;
import java.util.List;

/** Reads saved setup data for a game without accessing JavaFX controls. */
public final class GameSetupService {
    public record Setup(List<CategoryDao.CategoryRecord> categories, List<PlayerDao.PlayerRecord> players) {}
    public record Filters(int categoryId, String difficulty, String type) {}
    private final CategoryDao categories = new CategoryDao();
    private final QuestionDao questions = new QuestionDao();
    private final PlayerDao players = new PlayerDao();
    public Setup setup() throws SQLException { return new Setup(categories.getAllCategories(), players.getAllPlayers()); }
    public int count(Filters filters) throws SQLException {
        return questions.countMatchingQuestions(filters.categoryId(), filters.difficulty(), filters.type());
    }
    public List<QuestionDao.QuestionRecord> matching(Filters filters) throws SQLException {
        var pool = filters.categoryId() == 0 ? questions.getAllQuestions() : categories.getQuestionsRecursively(filters.categoryId());
        return pool.stream().filter(q -> (filters.difficulty().equalsIgnoreCase("any") || q.difficulty().equalsIgnoreCase(filters.difficulty()))
                && (filters.type().equals("ANY") || filters.type().equals(q.questionType()))).toList();
    }
}
