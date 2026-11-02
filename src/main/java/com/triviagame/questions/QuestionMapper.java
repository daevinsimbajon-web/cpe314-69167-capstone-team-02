package com.triviagame.questions;

import com.triviagame.db.QuestionDao.QuestionRecord;

public class QuestionMapper {

    public static Question toQuestion(QuestionRecord record) {

        if ("TRUE_FALSE".equals(record.questionType())) {

            return new TrueFalseQuestion(
                record.id(),
                record.categoryId(),
                record.text(),
                record.correctAnswer()
            );
        }

        if ("MULTIPLE_CHOICE".equals(record.questionType())) {

            String[] incorrect =
                parseAnswers(record.incorrectAnswers());

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

        throw new IllegalArgumentException(
            "Unsupported question type: " + record.questionType()
        );
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
