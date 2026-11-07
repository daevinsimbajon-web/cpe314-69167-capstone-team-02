package com.triviagame.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class OpenTdbClient {

    private final HttpClient client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

    private final QuestionParser parser =
            new QuestionParser();

    public List<BankQuestion> fetchQuestions(int amount)
            throws IOException, InterruptedException {

        if (amount < 1 || amount > 50) {
            throw new IllegalArgumentException(
                "OpenTDB amount must be between 1 and 50"
            );
        }

        URI endpoint = URI.create(
            "https://opentdb.com/api.php?amount="
            + amount + "&encode=url3986"
        );

        HttpRequest request =
                HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                "OpenTDB returned HTTP " + response.statusCode()
            );
        }

        List<BankQuestion> questions =
                parser.parse(response.body(), true);

        if (questions.isEmpty()) {
            throw new IOException(
                "OpenTDB returned no questions"
            );
        }

        return questions;
    }
}