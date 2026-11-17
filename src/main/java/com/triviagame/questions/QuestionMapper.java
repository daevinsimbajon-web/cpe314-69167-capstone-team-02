package com.triviagame.questions;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triviagame.db.QuestionDao.QuestionRecord;

import java.io.IOException;
import java.util.List;

public final class QuestionMapper {

    private static final ObjectMapper JSON =
            new ObjectMapper();

    private QuestionMapper() {
    }

    public static Question toQuestion(
            QuestionRecord record) {

        if ("TRUE_FALSE".equals(record.questionType())) {
            return new TrueFalseQuestion(
                record.id(),
                record.categoryId(),
                record.text(),
                record.correctAnswer()
            );
        }

        if ("MULTIPLE_CHOICE".equals(
                record.questionType())) {

            List<String> wrong;

            try {
                wrong = JSON.readValue(
                    record.incorrectAnswers(),
                    new TypeReference<List<String>>() {}
                );
            } catch (IOException e) {
                throw new IllegalArgumentException(
                    "Invalid question choices JSON for ID "
                    + record.id(), e
                );
            }

            if (wrong == null || wrong.isEmpty()) {
                throw new IllegalArgumentException(
                    "No choices for question " + record.id()
                );
            }

            String[] choices =
                    new String[wrong.size() + 1];

            choices[0] = record.correctAnswer();

            for (int i = 0; i < wrong.size(); i++) {
                choices[i + 1] = wrong.get(i);
            }

            return new MultipleChoiceQuestion(
                record.id(),
                record.categoryId(),
                record.text(),
                choices,
                record.correctAnswer()
            );
        }

        throw new IllegalArgumentException(
            "Unsupported question type: "
            + record.questionType()
        );
    }
}