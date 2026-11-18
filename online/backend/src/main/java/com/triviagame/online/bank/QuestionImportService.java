package com.triviagame.online.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triviagame.api.QuestionParser;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts question JSON into a private quiz draft.
 */
@Service
public class QuestionImportService {
    private final ObjectMapper json;

    public QuestionImportService(ObjectMapper json) {
        this.json = json;
    }

    public List<PersonalQuizzes.Question> draft(String source) {
        try {
            var document = json.readTree(source);
            if (document == null) {
                throw new IllegalArgumentException("Empty question file.");
            }
            var rows =
                    document.isArray()
                            ? document
                            : document.has("questions")
                                    ? document.get("questions")
                                    : document.get("results");
            if (rows != null && rows.isArray()) {
                for (var row : rows) {
                    if (row instanceof ObjectNode object) {
                        object.put("difficulty", "easy");
                    }
                }
            }
            return new QuestionParser()
                    .parse(json.writeValueAsString(document)).stream()
                            .map(q -> {
                                var options = new ArrayList<>(q.incorrectAnswers());
                                options.add(0, q.correctAnswer());
                                if (q.questionType().equals("TRUE_FALSE")) {
                                    options = new ArrayList<>(List.of("True", "False"));
                                }
                                if (options.size() > 6) {
                                    throw new IllegalArgumentException("Use 2–6 answer options.");
                                }
                                int correct =
                                        q.questionType().equals("TRUE_FALSE")
                                                ? (q.correctAnswer().equalsIgnoreCase("true")
                                                        ? 0
                                                        : 1)
                                                : options.indexOf(q.correctAnswer());
                                return new PersonalQuizzes.Question(
                                        q.questionType(), q.text(), options, correct, null);
                            })
                            .toList();
        } catch (IOException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Could not import these questions. Check the format and use 2–6 answer"
                        + " options.");
        }
    }
}
