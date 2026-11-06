package com.triviagame.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class QuestionParserTest {

    private final QuestionParser parser =
            new QuestionParser();

    @Test
    void parsesCustomQuestionBankArray() throws Exception {
        String json = """
            [{"category":"Science","text":"Water formula?",
              "correctAnswer":"H2O",
              "incorrectAnswers":["CO2","NaCl"],
              "difficulty":"easy",
              "questionType":"MULTIPLE_CHOICE"}]
            """;

        var result = parser.parse(json);

        assertEquals(1, result.size());
        assertEquals("H2O", result.get(0).correctAnswer());
        assertEquals(
            2, result.get(0).incorrectAnswers().size()
        );
    }

    @Test
    void parsesActualOpenTdbShapeAndDecodesUtf8()
            throws Exception {

        String json = """
            {"response_code":0,"results":[{
              "category":"Science%3A%20Computers",
              "type":"multiple",
              "difficulty":"easy",
              "question":"Which%20language%3F",
              "correct_answer":"C%2B%2B",
              "incorrect_answers":["Java","Python","Rust"]
            }]}
            """;

        var result = parser.parse(json, true);

        assertEquals(
            "Science: Computers", result.get(0).category()
        );
        assertEquals(
            "Which language?", result.get(0).text()
        );
        assertEquals(
            "C++", result.get(0).correctAnswer()
        );
    }

    @Test
    void acceptsTrueFalseOpenTdbQuestion() throws Exception {
        var result = parser.parse("""
            {"response_code":0,"results":[{
              "category":"Science",
              "type":"boolean",
              "difficulty":"easy",
              "question":"Is sky blue?",
              "correct_answer":"True",
              "incorrect_answers":["False"]
            }]}
            """);

        assertEquals(
            "TRUE_FALSE", result.get(0).questionType()
        );
    }

    @Test
    void rejectsRateLimitOrNoResults() {
        assertThrows(IOException.class,
            () -> parser.parse(
                "{\"response_code\":5,\"results\":[]}"
            )
        );
    }

    @Test
    void rejectsBrokenAnswerList() {
        assertThrows(IOException.class, () -> parser.parse("""
            [{"category":"History","text":"Q",
              "correctAnswer":"A",
              "incorrectAnswers":"B",
              "difficulty":"easy",
              "questionType":"MULTIPLE_CHOICE"}]
            """));
    }

    @Test
    void rejectsUnknownQuestionType() {
        assertThrows(IOException.class, () -> parser.parse("""
            [{"category":"History","text":"Q",
              "correctAnswer":"A",
              "incorrectAnswers":["B"],
              "difficulty":"easy",
              "questionType":"ESSAY"}]
            """));
    }
}