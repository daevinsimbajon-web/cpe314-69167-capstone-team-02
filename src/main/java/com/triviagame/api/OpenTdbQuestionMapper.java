package com.triviagame.api;

import com.triviagame.dto.opentdb.OpenTdbQuestionDto;
import java.util.List;
import java.util.Locale;

/** Converts validated upstream fields into the existing question-bank model. */
public final class OpenTdbQuestionMapper {
  public BankQuestion map(OpenTdbQuestionDto input) {
    if (input == null || input.type() == null || input.incorrectAnswers() == null) {
      throw new IllegalArgumentException("Question fields are missing.");
    }
    String type =
        switch (input.type()) {
          case "multiple" -> "MULTIPLE_CHOICE";
          case "boolean" -> "TRUE_FALSE";
          default -> throw new IllegalArgumentException("Unsupported upstream question type.");
        };
    String correct = decode(input.correctAnswer());
    List<String> wrong = input.incorrectAnswers().stream().map(this::decode).toList();
    if (type.equals("TRUE_FALSE")
        && (wrong.size() != 1
            || !List.of("true", "false").contains(wrong.get(0).toLowerCase(Locale.ROOT)))) {
      throw new IllegalArgumentException("Invalid true/false choices.");
    }
    if (wrong.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count()
        != wrong.size()) {
      throw new IllegalArgumentException("Duplicate answer choices.");
    }
    return new BankQuestion(
        decode(input.category()),
        decode(input.question()),
        correct,
        wrong,
        input.difficulty(),
        type);
  }

  private String decode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Question text is missing.");
    }
    return QuestionParser.displayText(value);
  }
}
