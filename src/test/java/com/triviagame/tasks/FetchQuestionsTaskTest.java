package com.triviagame.tasks;

import static org.junit.jupiter.api.Assertions.*;

import com.triviagame.api.BankQuestion;
import com.triviagame.service.QuestionBankService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FetchQuestionsTaskTest {
    @Test
    void workUsesSelectedFiltersAndImportsFetchedQuestions() throws Exception {
        var question = new BankQuestion(
                "Science", "A test question?", "True", List.of("False"),
                "easy", "TRUE_FALSE");
        var fetched = List.of(question);
        var expected = new QuestionBankService.ImportReport(1, 1, 0);
        var imported = new AtomicBoolean();
        var workerThread = new AtomicReference<String>();
        var task = new FetchQuestionsTask(
                7, "easy", "boolean",
                (amount, difficulty, type) -> {
                    workerThread.set(Thread.currentThread().getName());
                    assertEquals(7, amount);
                    assertEquals("easy", difficulty);
                    assertEquals("boolean", type);
                    return fetched;
                },
                (questions, source) -> {
                    assertSame(fetched, questions);
                    assertEquals("OpenTDB", source);
                    imported.set(true);
                    return expected;
                });
        List<String> messages = new ArrayList<>();

        var executor = Executors.newSingleThreadExecutor(job ->
                new Thread(job, "question-fetch-test-worker"));
        try {
            var result = executor.submit(
                    () -> task.executeWork(messages::add));
            assertSame(expected, result.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals("question-fetch-test-worker", workerThread.get());
        assertTrue(imported.get());
        assertEquals(3, messages.size());
        assertEquals("Downloading 7 questions...", messages.get(0));
        assertEquals("Adding questions to your collection...", messages.get(1));
        assertEquals("Added 1 new questions.", messages.get(2));
    }

    @Test
    void fetchFailureIsPropagatedAndQuestionsAreNotImported() {
        var task = new FetchQuestionsTask(
                2, "any", "any",
                (amount, difficulty, type) -> {
                    throw new IOException("provider unavailable");
                },
                (questions, source) -> {
                    fail("Import must not run after fetch failure");
                    return null;
                });

        var messages = new ArrayList<String>();
        var error = assertThrows(
                IOException.class, () -> task.executeWork(messages::add));

        assertEquals("provider unavailable", error.getMessage());
        assertEquals(List.of("Downloading 2 questions..."), messages);
    }
}
