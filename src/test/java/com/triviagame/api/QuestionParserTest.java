package com.triviagame.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class QuestionParserTest {

    private final QuestionParser parser =
            new QuestionParser();

    static Stream<Arguments> entities() {
        return Stream.of(
                Arguments.of("&amp;", "&"), Arguments.of("&quot;", "\""),
                Arguments.of("&#039;", "'"), Arguments.of("&#x27;", "'"),
                Arguments.of("&lt;", "<"), Arguments.of("&gt;", ">"),
                Arguments.of("&nbsp;", " "), Arguments.of("&eacute;", "é"),
                Arguments.of("&Eacute;", "É"), Arguments.of("&copy;", "©"),
                Arguments.of("&ndash;", "–"), Arguments.of("&mdash;", "—"),
                Arguments.of("&hellip;", "…"), Arguments.of("&rsquo;", "’"),
                Arguments.of("&ldquo;", "“"), Arguments.of("&rdquo;", "”"),
                Arguments.of("&#128512;", "😀"), Arguments.of("&#X1F600;", "😀"),
                Arguments.of("&AMP;", "&"), Arguments.of("&Ouml;", "Ö"),
                Arguments.of("&lsquo;", "‘"));
    }

    private BankQuestion parseValues(String question, String correct, List<String> wrong)
            throws Exception {
        String json = new ObjectMapper().writeValueAsString(List.of(Map.of(
                "category", "Science", "question", question, "correct_answer", correct,
                "incorrect_answers", wrong, "difficulty", "easy", "type", "multiple")));
        return parser.parse(json).get(0);
    }

    @ParameterizedTest
    @MethodSource("entities")
    void decodesEntitiesInQuestionAndEveryAnswer(String encoded, String decoded) throws Exception {
        var question = parseValues("Q " + encoded + " end", "A " + encoded + " end",
                List.of("B " + encoded + " end", "C " + encoded + " end", "D " + encoded + " end"));
        assertEquals("Q " + decoded + " end", question.text());
        assertEquals("A " + decoded + " end", question.correctAnswer());
        assertEquals(List.of("B " + decoded + " end", "C " + decoded + " end", "D " + decoded + " end"),
                question.incorrectAnswers());
    }

    @Test
    void preservesPlainUnicodeAndLiteralPlusSigns() throws Exception {
        var question = parseValues("C++ & café — ‘hello’ <3", "É + ñ", List.of("© + ®", "普通话"));
        assertEquals("C++ & café — ‘hello’ <3", question.text());
        assertEquals("É + ñ", question.correctAnswer());
        assertEquals(List.of("© + ®", "普通话"), question.incorrectAnswers());
    }

    @Test
    void percentEncodingAndHtmlDecodeTogether() throws Exception {
        var question = parser.parse("""
                {"response_code":0,"results":[{"category":"Science","type":"multiple",
                "difficulty":"easy","question":"C%2B%2B%20%26amp%3B%20caf%C3%A9",
                "correct_answer":"%26quot%3BA%26quot%3B",
                "incorrect_answers":["%26lt%3BB%26gt%3B","%26%23039%3BC%26%23x27%3B"]}]}
                """).get(0);
        assertEquals("C++ & café", question.text());
        assertEquals("\"A\"", question.correctAnswer());
        assertEquals(List.of("<B>", "'C'"), question.incorrectAnswers());
    }

    @Test
    void retainsUnknownEntitiesAndHandlesDoubleEncoding() throws Exception {
        var question = parseValues("&amp;quot;Q&amp;quot;", "&unknown;", List.of("&#99999999;", "B"));
        assertEquals("\"Q\"", question.text());
        assertEquals("&unknown;", question.correctAnswer());
        assertEquals("&#99999999;", question.incorrectAnswers().get(0));
        assertEquals("“C++” — café", QuestionParser.displayText("&ldquo;C++&rdquo; &mdash; caf&eacute;"));
    }

    @Test
    void supportsOptionalProviderIdWithoutDecodingIt() throws Exception {
        var question = parser.parse("""
                [{"category":"Science","type":"multiple","difficulty":"easy",
                "question":"Q","correct_answer":"A","incorrect_answers":["B"],"external_id":"id%2B&amp;"}]
                """).get(0);
        assertEquals("id%2B&amp;", question.externalId());
    }

    @Test
    void rejectsInvalidJsonAndNonTextAnswers() {
        assertThrows(IOException.class, () -> parser.parse("{broken"));
        assertThrows(IOException.class, () -> parser.parse("""
                [{"category":"Science","type":"multiple","difficulty":"easy",
                "question":"Q","correct_answer":"A","incorrect_answers":[1]}]
                """));
    }

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
