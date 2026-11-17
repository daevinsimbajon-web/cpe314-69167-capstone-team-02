package com.triviagame.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

class OpenTdbContractTest {
    @Test
    void capturedOfficialResponseMapsIntoDecodedDomainFields() throws Exception {
        try (var stream =
                Objects.requireNonNull(
                        getClass().getResourceAsStream("/opentdb/live-response-2026-11-17.json"))) {
            var response =
                    new OpenTdbResponseParser()
                            .questions(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(0, response.responseCode());
            var question = new OpenTdbQuestionMapper().map(response.results().get(0));
            assertEquals("Entertainment: Film", question.category());
            assertEquals("1985", question.correctAnswer());
            assertEquals("MULTIPLE_CHOICE", question.questionType());
        }
    }

    @Test
    void upstreamAliasesAndCoercedFieldTypesAreNotAccepted() throws Exception {
        var parser = new OpenTdbResponseParser();
        assertThrows(
                java.io.IOException.class,
                () -> parser.questions("{\"response_code\":\"0\",\"results\":[]}"));
        var aliases =
                parser.questions(
                        """
                        {"response_code":0,"results":[{"category":"Science","type":"boolean",
                        "difficulty":"easy","text":"Alias","correctAnswer":"True",
                        "incorrectAnswers":["False"]}]}
                        """);
        assertThrows(
                IllegalArgumentException.class,
                () -> new OpenTdbQuestionMapper().map(aliases.results().get(0)));
    }
}
