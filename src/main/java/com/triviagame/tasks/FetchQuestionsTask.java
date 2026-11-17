
package com.triviagame.tasks;

import com.triviagame.api.OpenTdbClient;
import com.triviagame.db.QuestionBankService;

import javafx.concurrent.Task;

public final class FetchQuestionsTask
        extends Task<QuestionBankService.ImportReport> {

    private final int amount;
    private final String difficulty;
    private final String type;

    public FetchQuestionsTask(int amount) {
        this(amount, "any", "any");
    }

    public FetchQuestionsTask(
            int amount,
            String difficulty,
            String type) {

        this.amount = amount;
        this.difficulty = difficulty;
        this.type = type;
    }

    @Override
    protected QuestionBankService.ImportReport call()
            throws Exception {

        updateMessage(
                "Downloading " + amount + " questions..."
        );

        var questions =
                new OpenTdbClient().fetchQuestions(
                        amount,
                        difficulty,
                        type
                );

        if (isCancelled()) {
            return null;
        }

        updateMessage(
                "Adding questions to your collection..."
        );

        var report =
                new QuestionBankService().importQuestions(
                        questions,
                        "OpenTDB"
                );

        updateMessage(
                "Added " + report.inserted()
                + " new questions."
        );

        return report;
    }
}
