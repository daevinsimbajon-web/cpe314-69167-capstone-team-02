package com.triviagame.service;

import com.triviagame.dao.CategoryDao;
import com.triviagame.dao.Database;
import com.triviagame.dao.PlayerDao;
import com.triviagame.dao.QuestionDao;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the setup/controller boundary against an isolated SQLite database. */
class GameSetupServiceTest {
    @TempDir Path directory;
    private String previousPath;
    @BeforeEach void setup() throws Exception {
        previousPath = System.getProperty("trivia.database.path");
        System.setProperty("trivia.database.path", directory.resolve("setup.db").toString());
        Database.initializeSchema();
    }
    @AfterEach void restore() {
        if (previousPath == null) System.clearProperty("trivia.database.path");
        else System.setProperty("trivia.database.path", previousPath);
    }
    @Test void filtersCountAndLoadTheSameQuestionsAcrossNestedCategories() throws Exception {
        var category = new CategoryDao();
        int root = category.addCategory("Science");
        int child = category.addCategory("Space", root);
        int grandchild = category.addCategory("Stars", child);
        int other = category.addCategory("History");
        var questions = new QuestionDao();
        questions.addQuestion("A star?", "True", "[\"False\"]", "easy", grandchild, "TRUE_FALSE");
        questions.addQuestion("A planet?", "Earth", "[\"Sun\",\"Moon\"]", "hard", child, "MULTIPLE_CHOICE");
        questions.addQuestion("Past event?", "True", "[\"False\"]", "easy", other, "TRUE_FALSE");
        new PlayerDao().addPlayer("Player");
        var service = new GameSetupService();
        var selection = new GameSetupService.Filters(root, "easy", "TRUE_FALSE");
        assertEquals(1, service.count(selection));
        assertEquals(service.count(selection), service.matching(selection).size());
        assertEquals("A star?", service.matching(selection).get(0).text());
        assertEquals(2, service.count(new GameSetupService.Filters(root, "Any", "ANY")));
        assertEquals(3, service.count(new GameSetupService.Filters(0, "Any", "ANY")));
        assertEquals(1, service.setup().players().size());
    }
}
