package com.triviagame.tasks;

import com.triviagame.api.OpenTdbClient;
import com.triviagame.db.QuestionBankService;
import javafx.concurrent.Task;

public final class FetchQuestionsTask
        extends Task<QuestionBankService.ImportReport> {

    private final int amount;

    public FetchQuestionsTask(int amount) {
        this.amount = amount;
    }

    @Override
    protected QuestionBankService.ImportReport call()
            throws Exception {

        updateMessage(
            "Fetching " + amount + " questions from OpenTDB..."
        );

        var questions =
                new OpenTdbClient().fetchQuestions(amount);

        if (isCancelled()) {
            return null;
        }

        updateMessage("Saving questions to SQLite bank...");

        var report = new QuestionBankService()
                .importQuestions(questions, "OpenTDB");

        updateMessage(
            "Saved " + report.inserted() + " new questions"
        );

        return report;
    }
}