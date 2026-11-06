package com.triviagame.api;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record BankQuestion(
        String category,
        String text,
        String correctAnswer,
        List<String> incorrectAnswers,
        String difficulty,
        String questionType) {

    public BankQuestion {
        category = required(category, "category");
        text = required(text, "question text");
        correctAnswer = required(correctAnswer, "correct answer");
        difficulty = required(difficulty, "difficulty")
                .toLowerCase(Locale.ROOT);
        questionType = required(questionType, "question type")
                .toUpperCase(Locale.ROOT);

        if (!List.of("easy", "medium", "hard").contains(difficulty)) {
            throw new IllegalArgumentException(
                "Difficulty must be easy, medium, or hard"
            );
        }

        if (!List.of("MULTIPLE_CHOICE", "TRUE_FALSE")
                .contains(questionType)) {
            throw new IllegalArgumentException(
                "Unsupported question type: " + questionType
            );
        }

        Objects.requireNonNull(
            incorrectAnswers, "incorrect answers"
        );

        if (incorrectAnswers.isEmpty()) {
            throw new IllegalArgumentException(
                "A question needs incorrect answers"
            );
        }

        incorrectAnswers = incorrectAnswers.stream()
                .map(value -> required(value, "incorrect answer"))
                .toList();

        for (String wrong : incorrectAnswers) {
            if (wrong.equalsIgnoreCase(correctAnswer)) {
                throw new IllegalArgumentException(
                    "Correct answer cannot also be incorrect"
                );
            }
        }

        if (questionType.equals("TRUE_FALSE") &&
                (!List.of("true", "false")
                .contains(correctAnswer.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException(
                "True/false question answer must be True or False"
            );
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Missing " + label
            );
        }
        return value.trim();
    }
}