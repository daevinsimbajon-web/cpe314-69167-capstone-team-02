package com.triviagame.db;

import com.triviagame.api.BankQuestion;
import com.triviagame.api.QuestionParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class QuestionImportTest {
    @TempDir Path directory;
    private String previousPath;
    private final QuestionBankService service = new QuestionBankService();

    @BeforeEach
    void isolateDatabase() {
        previousPath = System.getProperty("trivia.database.path");
        System.setProperty("trivia.database.path", directory.resolve("questions.db").toString());
    }

    @AfterEach
    void restoreDatabase() {
        if (previousPath == null) {
            System.clearProperty("trivia.database.path");
        } else {
            System.setProperty("trivia.database.path", previousPath);
        }
    }

    private BankQuestion question(String text, String id) {
        return new BankQuestion("Import test", text, "A", List.of("B", "C"),
                "easy", "MULTIPLE_CHOICE", id);
    }

    private int scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    @Test
    void freshSchemaAndDaoSaveIdsAndTimestamps() throws Exception {
        Database.initializeSchema();
        int category = new CategoryDao().addCategory("Fresh");
        QuestionDao dao = new QuestionDao();
        int id = dao.addQuestion("Fresh question", "A", "[\"B\"]", "easy",
                category, "MULTIPLE_CHOICE", "provider:42");
        var saved = dao.getQuestionById(id).orElseThrow();
        assertEquals("provider:42", saved.externalId());
        assertNotNull(LocalDateTime.parse(saved.createdAt(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        assertThrows(SQLException.class, () -> dao.addQuestion("Another", "A", "[\"B\"]",
                "easy", category, "MULTIPLE_CHOICE", "provider:42"));
        int legacyId = dao.addQuestion("Legacy caller", "A", "[\"B\"]", "easy", category);
        assertNull(dao.getQuestionById(legacyId).orElseThrow().externalId());
        assertNotNull(dao.getQuestionById(legacyId).orElseThrow().createdAt());
        dao.updateQuestion(id, "Edited", "A", "[\"B\"]", "medium", category);
        Database.initializeSchema();
        var updated = dao.getQuestionById(id).orElseThrow();
        assertEquals(saved.createdAt(), updated.createdAt());
        assertEquals(saved.externalId(), updated.externalId());
    }

    @Test
    void legacyMigrationPreservesDuplicateRowsAndRelationships() throws Exception {
        try (Connection connection = Database.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE categories (id INTEGER PRIMARY KEY, name TEXT NOT NULL, parent_id INTEGER)");
            statement.execute("INSERT INTO categories VALUES (7, 'Legacy', NULL)");
            statement.execute("""
                    CREATE TABLE questions (id INTEGER PRIMARY KEY, text TEXT NOT NULL,
                    correct_answer TEXT NOT NULL, incorrect_answers TEXT NOT NULL,
                    difficulty TEXT NOT NULL, category_id INTEGER NOT NULL REFERENCES categories(id))
                    """);
            statement.execute("INSERT INTO questions VALUES (11, 'Old', 'A', '[\"B\"]', 'easy', 7), (12, 'Old', 'A', '[\"B\"]', 'easy', 7)");
            statement.execute("CREATE TABLE saved_question (question_id INTEGER REFERENCES questions(id))");
            statement.execute("INSERT INTO saved_question VALUES (12)");
            Database.initializeSchema(connection);
            Database.initializeSchema(connection);
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM questions"));
            assertEquals(12, scalar(connection, "SELECT q.id FROM questions q JOIN saved_question s ON s.question_id=q.id"));
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM questions WHERE datetime(created_at) IS NOT NULL AND category_id=7 AND question_type='MULTIPLE_CHOICE'"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM pragma_foreign_key_check"));
            statement.execute("INSERT INTO questions (id,text,correct_answer,incorrect_answers,difficulty,category_id) VALUES (13,'New','A','[\"B\"]','easy',7)");
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM questions WHERE id=13 AND datetime(created_at) IS NOT NULL"));
        }
    }

    @Test
    void preexistingDuplicateExternalIdsKeepAllRows() throws Exception {
        try (Connection connection = Database.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE categories (id INTEGER PRIMARY KEY, name TEXT, parent_id INTEGER)");
            statement.execute("INSERT INTO categories VALUES (1,'Legacy',NULL)");
            statement.execute("""
                    CREATE TABLE questions (id INTEGER PRIMARY KEY, text TEXT, correct_answer TEXT,
                    incorrect_answers TEXT, difficulty TEXT, category_id INTEGER, external_id TEXT, created_at TEXT)
                    """);
            statement.execute("INSERT INTO questions VALUES (1,'Q','A','[]','easy',1,'same','2020-01-02 03:04:05'), (2,'Q','A','[]','easy',1,'same',NULL)");
            Database.initializeSchema(connection);
            Database.initializeSchema(connection);
            assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM questions"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM questions WHERE external_id='same' AND id=1 AND created_at='2020-01-02 03:04:05'"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM questions WHERE id=2 AND external_id IS NULL AND datetime(created_at) IS NOT NULL"));
        }
    }

    @Test
    void repeatedJsonImportsHaveAccurateReportsAndStableIdentity() throws Exception {
        var questions = new QuestionParser().parse("""
                [{"category":"Import test","question":"First?","correct_answer":"A",
                  "incorrect_answers":["B"],"difficulty":"easy","type":"multiple"},
                 {"category":"Import test","question":"Second?","correct_answer":"True",
                  "incorrect_answers":["False"],"difficulty":"medium","type":"boolean","id":42}]
                """);
        assertEquals(new QuestionBankService.ImportReport(2, 2, 0), service.importQuestions(questions, "JSON:bank.json"));
        var saved = new QuestionDao().getAllQuestions().stream()
                .filter(q -> q.text().equals("First?")).findFirst().orElseThrow();
        assertEquals(QuestionBankService.externalId("JSON:bank.json", questions.get(0)), saved.externalId());
        assertEquals(new QuestionBankService.ImportReport(2, 0, 2), service.importQuestions(questions, "JSON:bank.json"));
        assertEquals(saved, new QuestionDao().getQuestionById(saved.id()).orElseThrow());
        assertEquals(new QuestionBankService.ImportReport(1, 1, 0), service.importQuestions(List.of(question("Third?", null)), "JSON:bank.json"));
    }

    @Test
    void providerIdsAreSourceScopedAndFallbackIsNormalized() throws Exception {
        var original = question("Question?", "42");
        service.importQuestions(List.of(original), "OpenTDB");
        assertEquals(0, service.importQuestions(List.of(question("Revised question?", "42")), "OpenTDB").inserted());
        assertNotEquals(QuestionBankService.externalId("Other", original), QuestionBankService.externalId("OpenTDB", original));
        assertEquals(QuestionBankService.externalId(" OpenTDB ", question(" A   question? ", null)),
                QuestionBankService.externalId("opentdb", question("a question?", null)));
        assertEquals(0, service.importQuestions(List.of(question(" QUESTION? ", null)), "Another source").inserted());
    }

    @Test
    void duplicatesWithinOneBatchAreCounted() throws Exception {
        var q = question("Same batch?", null);
        assertEquals(new QuestionBankService.ImportReport(3, 1, 2), service.importQuestions(List.of(q, q, q), "OpenTDB"));
    }

    @Test
    void concurrentImportsDoNotDuplicateQuestionsOrCategories() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<QuestionBankService.ImportReport> task = () -> {
            start.await();
            return new QuestionBankService().importQuestions(List.of(question("Concurrent?", null)), "OpenTDB");
        };
        try {
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            var a = first.get(30, TimeUnit.SECONDS);
            var b = second.get(30, TimeUnit.SECONDS);
            assertEquals(1, a.inserted() + b.inserted());
            assertEquals(1, a.duplicates() + b.duplicates());
            try (var connection = Database.getConnection()) {
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM categories WHERE name='Import test'"));
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM questions WHERE text='Concurrent?'"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unexpectedSqlErrorsPropagateAndRollBackImport() throws Exception {
        service.initializeBank();
        try (var connection = Database.getConnection(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER reject_test_question BEFORE INSERT ON questions
                    WHEN NEW.text='Reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END
                    """);
        }
        assertThrows(SQLException.class, () -> service.importQuestions(
                List.of(question("Before rejected", null), question("Reject", null)), "OpenTDB"));
        try (var connection = Database.getConnection()) {
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM questions WHERE text='Before rejected'"));
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM categories WHERE name='Import test'"));
        }
    }
}
