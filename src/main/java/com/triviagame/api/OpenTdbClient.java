
package com.triviagame.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class OpenTdbClient {

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .build();

    private final QuestionParser parser =
            new QuestionParser();

    public List<BankQuestion> fetchQuestions(int amount)
            throws IOException, InterruptedException {

        return fetchQuestions(amount, "any", "any");
    }

    public List<BankQuestion> fetchQuestions(
            int amount,
            String difficulty,
            String type)
            throws IOException, InterruptedException {

        if (amount < 1 || amount > 50) {
            throw new IllegalArgumentException(
                    "Question amount must be between 1 and 50."
            );
        }

        difficulty = normalize(difficulty);
        type = normalize(type);

        if (!List.of(
                "any", "easy", "medium", "hard"
        ).contains(difficulty)) {
            throw new IllegalArgumentException(
                    "Invalid difficulty selection."
            );
        }

        if (!List.of(
                "any", "multiple", "boolean"
        ).contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid question type selection."
            );
        }

        StringBuilder endpoint = new StringBuilder(
                "https://opentdb.com/api.php?amount="
                + amount
                + "&encode=url3986"
        );

        if (!difficulty.equals("any")) {
            endpoint.append("&difficulty=")
                    .append(difficulty);
        }

        if (!type.equals("any")) {
            endpoint.append("&type=")
                    .append(type);
        }

        HttpRequest request =
                HttpRequest.newBuilder(
                        URI.create(endpoint.toString())
                )
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Question service returned HTTP "
                    + response.statusCode()
            );
        }

        List<BankQuestion> questions =
                parser.parse(
                        response.body(),
                        true
                );

        if (questions.isEmpty()) {
            throw new IOException(
                    "No questions match the selected filters."
            );
        }

        return questions;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? "any"
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
