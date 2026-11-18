package com.triviagame.tasks;

import com.triviagame.api.BankQuestion;
import com.triviagame.api.OpenTdbClient;
import com.triviagame.service.QuestionBankService;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.concurrent.Task;

public final class FetchQuestionsTask extends Task<QuestionBankService.ImportReport> {

  @FunctionalInterface
  interface QuestionFetcher {
    List<BankQuestion> fetch(int amount, String difficulty, String type) throws Exception;
  }

  @FunctionalInterface
  interface QuestionImporter {
    QuestionBankService.ImportReport importQuestions(List<BankQuestion> questions, String source)
        throws Exception;
  }

  private final int amount;
  private final String difficulty;
  private final String type;
  private final QuestionFetcher fetcher;
  private final QuestionImporter importer;

  public FetchQuestionsTask(int amount) {
    this(amount, "any", "any");
  }

  public FetchQuestionsTask(int amount, String difficulty, String type) {
    this(
        amount,
        difficulty,
        type,
        new OpenTdbClient()::fetchQuestions,
        new QuestionBankService()::importQuestions);
  }

  FetchQuestionsTask(
      int amount,
      String difficulty,
      String type,
      QuestionFetcher fetcher,
      QuestionImporter importer) {
    this.amount = amount;
    this.difficulty = difficulty;
    this.type = type;
    this.fetcher = Objects.requireNonNull(fetcher);
    this.importer = Objects.requireNonNull(importer);
  }

  @Override
  protected QuestionBankService.ImportReport call() throws Exception {
    return executeWork(this::updateMessage);
  }

  QuestionBankService.ImportReport executeWork(Consumer<String> messageUpdater) throws Exception {
    Objects.requireNonNull(messageUpdater);
    messageUpdater.accept("Downloading " + amount + " questions...");
    List<BankQuestion> questions = fetcher.fetch(amount, difficulty, type);

    if (isCancelled()) {
      return null;
    }

    messageUpdater.accept("Adding questions to your collection...");
    var report = importer.importQuestions(questions, "OpenTDB");
    messageUpdater.accept("Added " + report.inserted() + " new questions.");
    return report;
  }
}
