
package com.triviagame.gameplay;

import java.util.List;
import java.util.function.Consumer;

import com.triviagame.api.QuestionParser;
import com.triviagame.db.CategoryDao;
import com.triviagame.db.PlayerDao;
import com.triviagame.db.QuestionBankService;
import com.triviagame.db.QuestionDao;
import com.triviagame.tasks.BackgroundTaskRunner;

import javafx.concurrent.Task;

public class NewGameController {

    private final CategoryDao categoryDao = new CategoryDao();
    private final PlayerDao playerDao = new PlayerDao();
    private final QuestionBankService bank = new QuestionBankService();

    public record SetupData(
            List<CategoryDao.CategoryRecord> categories,
            List<PlayerDao.PlayerRecord> players
    ) {
    }

    public void loadSetup(
            Consumer<SetupData> onSuccess,
            Consumer<Throwable> onError) {

        Task<SetupData> task = new Task<>() {

            @Override
            protected SetupData call() throws Exception {

                return new SetupData(
                        categoryDao.getAllCategories(),
                        playerDao.getAllPlayers()
                );
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }

    public void loadMatchingQuestions(
            int categoryId,
            String difficulty,
            String questionType,
            Consumer<List<QuestionDao.QuestionRecord>> onSuccess,
            Consumer<Throwable> onError) {

        Task<List<QuestionDao.QuestionRecord>> task =
                new Task<>() {

            @Override
            protected List<QuestionDao.QuestionRecord> call()
                    throws Exception {

                List<QuestionDao.QuestionRecord> pool;

                if (categoryId == 0) {
                    pool = bank.getQuestions(0);
                } else {
                    pool = categoryDao.getQuestionsRecursively(
                            categoryId
                    );
                }

                return pool.stream()
                        .filter(question ->
                                difficulty.equals("Any")
                                || question.difficulty()
                                        .equalsIgnoreCase(difficulty)
                        )
                        .filter(question ->
                                questionType.equals("Any")
                                || (questionType.equals("Multiple Choice")
                                    && question.questionType()
                                            .equals("MULTIPLE_CHOICE"))
                                || (questionType.equals("True / False")
                                    && question.questionType()
                                            .equals("TRUE_FALSE"))
                        )
                        .toList();
            }
        };

        task.setOnSucceeded(event ->
                onSuccess.accept(task.getValue()));

        task.setOnFailed(event ->
                onError.accept(task.getException()));

        BackgroundTaskRunner.run(task);
    }
}
