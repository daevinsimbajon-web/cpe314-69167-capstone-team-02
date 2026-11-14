package com.triviagame.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class OpenTdbClientTest {
    private static final String VALID_RESPONSE = """
            {"response_code":0,"results":[{
              "category":"Science%3A%20Computers",
              "type":"multiple","difficulty":"easy",
              "question":"Which%20language%20uses%20%26quot%3B%2B%2B%26quot%3B%3F",
              "correct_answer":"C%2B%2B",
              "incorrect_answers":["Java","Python","Rust"]
            }]}
            """;

    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private HttpServer server;
    private ExecutorService executor;
    private URI endpoint;
    private OpenTdbClient client;
    private volatile int status = 200;
    private volatile String body = VALID_RESPONSE;
    private volatile boolean holdResponse;

    @BeforeEach
    void startFakeService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/api.php", this::respond);
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api.php");
        client = new OpenTdbClient(endpoint, Duration.ofSeconds(3));
    }

    @AfterEach
    void stopFakeService() throws InterruptedException {
        releaseResponse.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Fake server must stop");
        }
    }

    @Test
    void parsesSuccessfulResponseAndDecodesQuestionAndAnswers() throws Exception {
        assertEquals(List.of(new BankQuestion(
                "Science: Computers", "Which language uses \"++\"?", "C++",
                List.of("Java", "Python", "Rust"), "easy", "MULTIPLE_CHOICE")),
                client.fetchQuestions(1));

        Request request = nextRequest();
        assertEquals("GET", request.method());
        assertEquals("application/json", request.accept());
    }

    @Test
    void reportsHttpErrorBeforeTryingToParseBody() {
        status = 500;
        body = "<html>Service unavailable</html>";

        IOException failure = assertThrows(IOException.class, () -> client.fetchQuestions(1));
        assertEquals("Question service returned HTTP 500", failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void reportsNoResultsAndRateLimitResponseCodes(int code) {
        body = "{\"response_code\":" + code + ",\"results\":[]}";

        IOException failure = assertThrows(IOException.class, () -> client.fetchQuestions(1));
        assertTrue(failure.getMessage().contains("response_code=" + code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{broken", "{\"response_code\":0,\"results\":[", "null",
            "{\"response_code\":0,\"results\":{}}", "{\"response_code\":0,\"results\":[{}]}"})
    void rejectsMalformedJsonOrInvalidResponseShape(String invalidBody) {
        body = invalidBody;

        assertThrows(IOException.class, () -> client.fetchQuestions(1));
    }

    @Test
    void rejectsEmptyResultsInsteadOfReportingSuccess() {
        body = "{\"response_code\":0,\"results\":[]}";

        IOException failure = assertThrows(IOException.class, () -> client.fetchQuestions(1));
        assertEquals("No questions match the selected filters.", failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 51, Integer.MAX_VALUE})
    void rejectsInvalidAmountsBeforeSendingRequest(int amount) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> client.fetchQuestions(amount));
        assertEquals("Question amount must be between 1 and 50.", failure.getMessage());
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expert", "eas y", "easy&amount=50"})
    void rejectsInvalidDifficultyBeforeSendingRequest(String difficulty) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> client.fetchQuestions(1, difficulty, "multiple"));
        assertEquals("Invalid difficulty selection.", failure.getMessage());
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"essay", "true_false", "boolean&amount=50"})
    void rejectsInvalidTypeBeforeSendingRequest(String type) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> client.fetchQuestions(1, "easy", type));
        assertEquals("Invalid question type selection.", failure.getMessage());
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"1, easy, multiple", "25, medium, boolean", "50, hard, multiple"})
    void sendsAmountEncodingAndNormalizedFilters(int amount, String difficulty, String type)
            throws Exception {
        client.fetchQuestions(amount, " " + difficulty.toUpperCase(java.util.Locale.ROOT) + " ",
                " " + type.toUpperCase(java.util.Locale.ROOT) + " ");

        assertEquals(Map.of("amount", Integer.toString(amount), "encode", "url3986",
                "difficulty", difficulty, "type", type), query(nextRequest()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "any", " AnY "})
    void omitsUnrestrictedFilters(String filter) throws Exception {
        client.fetchQuestions(1, filter, filter);

        assertEquals(Map.of("amount", "1", "encode", "url3986"), query(nextRequest()));
    }

    @Test
    void amountOnlyOverloadOmitsDifficultyAndType() throws Exception {
        client.fetchQuestions(10);

        assertEquals(Map.of("amount", "10", "encode", "url3986"), query(nextRequest()));
    }

    @Test
    void delayedResponseUsesInjectedTimeoutAndNextRequestCanSucceed() throws Exception {
        // Warm the local server before exercising the response timeout.
        client.fetchQuestions(1);
        nextRequest();
        holdResponse = true;
        OpenTdbClient impatientClient = new OpenTdbClient(endpoint, Duration.ofMillis(200));

        try {
            assertTimeout(Duration.ofSeconds(5), () ->
                    assertThrows(HttpTimeoutException.class, () -> impatientClient.fetchQuestions(1)));
            nextRequest(); // The server received it and deliberately withheld the response.
        } finally {
            holdResponse = false;
            releaseResponse.countDown();
        }

        assertEquals(1, client.fetchQuestions(1).size());
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI(),
                    exchange.getRequestHeaders().getFirst("Accept")));
            if (holdResponse && !releaseResponse.await(5, TimeUnit.SECONDS)) {
                return;
            }
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private Request nextRequest() throws InterruptedException {
        Request request = requests.poll(2, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the local fake service");
        assertEquals("/api.php", request.uri().getPath());
        return request;
    }

    private Map<String, String> query(Request request) {
        return Arrays.stream(request.uri().getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }

    private record Request(String method, URI uri, String accept) {
    }
}
