package com.triviagame;

import com.triviagame.db.QuestionDao.QuestionRecord;

public class QuestionMapper {

    public static Question toQuestion(QuestionRecord record) {

        String[] incorrect = parseAnswers(record.incorrectAnswers());

        if (incorrect.length == 0) {
            throw new IllegalArgumentException(
                "Question has no incorrect answers: " + record.text()
            );
        }

        // The database has no question-type column, so the type comes from the data:
        // a true/false question has exactly one incorrect answer, and both answers
        // are "True" or "False". Everything else is multiple choice.
        if (incorrect.length == 1
                && isTrueOrFalse(record.correctAnswer())
                && isTrueOrFalse(incorrect[0])) {

            return new TrueFalseQuestion(
                record.id(),
                record.categoryId(),
                record.text(),
                record.correctAnswer()
            );
        }

        String[] choices = new String[incorrect.length + 1];

        // Put the correct answer first.
        // Main will shuffle the choices before displaying.
        choices[0] = record.correctAnswer();

        System.arraycopy(
            incorrect, 0, choices, 1, incorrect.length
        );

        return new MultipleChoiceQuestion(
            record.id(),
            record.categoryId(),
            record.text(),
            choices,
            record.correctAnswer()
        );
    }

    private static boolean isTrueOrFalse(String answer) {
        return "true".equalsIgnoreCase(answer.trim())
            || "false".equalsIgnoreCase(answer.trim());
    }

    // Parses the simple JSON string array used by the seeder.
    private static String[] parseAnswers(String json) {

        String content = json.trim();

        if (!content.startsWith("[") || !content.endsWith("]")) {
            throw new IllegalArgumentException(
                "Invalid answer array"
            );
        }

        content = content.substring(1, content.length() - 1);

        if (content.isBlank()) {
            return new String[0];
        }

        String[] answers = content.split("\",\"");

        for (int i = 0; i < answers.length; i++) {
            answers[i] = answers[i]
                    .replaceAll("^\\s*\"|\"\\s*$", "")
                    .trim();
        }

        return answers;
    }
}