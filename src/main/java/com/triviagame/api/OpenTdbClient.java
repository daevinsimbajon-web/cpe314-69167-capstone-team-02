package com.triviagame.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public final class OpenTdbClient {

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(12))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private final OpenTdbResponseParser parser = new OpenTdbResponseParser();
  private final OpenTdbQuestionMapper mapper = new OpenTdbQuestionMapper();

  private final URI endpoint;
  private final Duration requestTimeout;

  public OpenTdbClient() {
    this(URI.create("https://opentdb.com/api.php"), Duration.ofSeconds(25));
  }

  // Package-private so tests can use a local service and a short timeout.
  public OpenTdbClient(URI endpoint, Duration requestTimeout) {
    this.endpoint = java.util.Objects.requireNonNull(endpoint);
    this.requestTimeout = java.util.Objects.requireNonNull(requestTimeout);
  }

  public List<BankQuestion> fetchQuestions(int amount) throws IOException, InterruptedException {

    return fetchQuestions(amount, "any", "any");
  }

  public List<BankQuestion> fetchQuestions(int amount, String difficulty, String type)
      throws IOException, InterruptedException {
    return fetchQuestions(amount, null, difficulty, type);
  }

  /** Additive category-aware API; existing desktop callers keep their original overloads. */
  public List<BankQuestion> fetchQuestions(
      int amount, Integer category, String difficulty, String type)
      throws IOException, InterruptedException {

    if (amount < 1 || amount > 50) {
      throw new IllegalArgumentException("Question amount must be between 1 and 50.");
    }

    difficulty = normalize(difficulty);
    type = normalize(type);

    if (!List.of("any", "easy", "medium", "hard").contains(difficulty)) {
      throw new IllegalArgumentException("Invalid difficulty selection.");
    }

    if (!List.of("any", "multiple", "boolean").contains(type)) {
      throw new IllegalArgumentException("Invalid question type selection.");
    }

    StringBuilder requestUrl =
        new StringBuilder(endpoint.toString() + "?amount=" + amount + "&encode=url3986");

    if (!difficulty.equals("any")) {
      requestUrl.append("&difficulty=").append(difficulty);
    }

    if (!type.equals("any")) {
      requestUrl.append("&type=").append(type);
    }
    if (category != null) {
      if (category <= 0) throw new IllegalArgumentException("Invalid category selection.");
      requestUrl.append("&category=").append(category);
    }

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(requestUrl.toString()))
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .GET()
            .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IOException("Question service returned HTTP " + response.statusCode());
    }

    var document = parser.questions(response.body());
    if (document == null || document.responseCode() == null || document.responseCode() < 0) {
      throw new IOException("Invalid OpenTDB response envelope.");
    }
    if (document.responseCode() != 0) {
      throw new ApiException(document.responseCode());
    }
    if (document.results() == null) {
      throw new IOException("OpenTDB results are missing.");
    }
    List<BankQuestion> questions;
    try {
      questions = document.results().stream().map(mapper::map).toList();
    } catch (IllegalArgumentException invalid) {
      throw new IOException("OpenTDB returned an invalid question.", invalid);
    }

    if (questions.isEmpty()) {
      throw new IOException("No questions match the selected filters.");
    }

    return questions;
  }

  public record Category(int id, String name) {}

  /** Typed application boundary; the exception API remains compatible with desktop callers. */
  public OpenTdbResult fetchResult(int amount, Integer category, String difficulty, String type)
      throws InterruptedException {
    try {
      return new OpenTdbResult(
          OpenTdbStatus.OK,
          fetchQuestions(amount, category, difficulty, type),
          0,
          "Questions ready.");
    } catch (ApiException failure) {
      return new OpenTdbResult(
          failure.code() == 1 ? OpenTdbStatus.EMPTY : OpenTdbStatus.FAILED,
          List.of(),
          failure.code(),
          "The provider could not prepare these questions.");
    } catch (IOException failure) {
      return new OpenTdbResult(
          OpenTdbStatus.FAILED,
          List.of(),
          null,
          "Instant Quiz is unavailable right now. Please try again shortly, or choose My"
              + " Quizzes.");
    }
  }

  public static class ApiException extends IOException {
    private final int code;

    public ApiException(int code) {
      super("OpenTDB response_code=" + code);
      this.code = code;
    }

    public int code() {
      return code;
    }
  }

  public List<Category> fetchCategories() throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(endpoint.resolve("api_category.php"))
            .timeout(requestTimeout)
            .header("Accept", "application/json")
            .GET()
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200)
      throw new IOException("Category service returned HTTP " + response.statusCode());
    return parseCategories(response.body());
  }

  /** Also validates persisted API caches. Never accepts local database category rows. */
  public static List<Category> parseCategories(String content) throws IOException {
    var root = new OpenTdbResponseParser().categories(content);
    var rows = root == null ? null : root.categories();
    if (rows == null || rows.isEmpty() || rows.size() > 200)
      throw new IOException("Invalid or empty official category list");
    var result = new ArrayList<Category>();
    var ids = new HashSet<Integer>();
    var names = new HashSet<String>();
    for (var row : rows) {
      if (row == null) throw new IOException("Invalid official category entry");
      var id = row.id();
      var name = row.name();
      if (id == null
          || id <= 0
          || name == null
          || name.isBlank()
          || name.length() > 200
          || !ids.add(id)
          || !names.add(name.strip())) throw new IOException("Invalid official category entry");
      result.add(new Category(id, name.strip()));
    }
    result.sort(java.util.Comparator.comparing(Category::name));
    return List.copyOf(result);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? "any" : value.trim().toLowerCase(Locale.ROOT);
  }
}
