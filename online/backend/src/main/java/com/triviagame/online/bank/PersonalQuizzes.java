package com.triviagame.online.bank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triviagame.api.BankQuestion;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-only drafts and immutable published question versions. Never rewrites game history. */
@Service
public class PersonalQuizzes {
  // Accept old draft/client metadata without exposing or validating custom difficulty.
  public record Question(
      String type,
      String text,
      List<String> options,
      @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = com.triviagame.online.StrictInteger.class)
          Integer correctIndex,
      @com.fasterxml.jackson.annotation.JsonProperty(
              access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
          String difficulty) {}

  public record Save(
      @NotBlank @Size(max = 120) String title,
      @Size(max = 2000) String description,
      UUID categoryId,
      @NotNull @Size(max = 50) List<Question> questions,
      @NotNull
          @com.fasterxml.jackson.databind.annotation.JsonDeserialize(
              using = com.triviagame.online.StrictInteger.class)
          Integer version,
      @NotNull @Pattern(regexp = "DRAFT|READY") String status) {}

  public record CategoryName(@NotBlank @Size(max = 100) String name, UUID parentId) {}

  public record Category(UUID id, String name, UUID parentId) {
    public Category(UUID id, String name) {
      this(id, name, null);
    }
  }

  public record Summary(
      UUID id,
      String title,
      String description,
      UUID categoryId,
      String category,
      String visibility,
      String status,
      int version,
      int questionCount,
      int multipleChoiceCount,
      int trueFalseCount,
      Instant createdAt,
      Instant updatedAt) {}

  public record Detail(Summary quiz, List<Question> questions) {}

  public record LibraryQuestion(UUID id, String category, Question question) {}

  public record Playable(UUID id, int version, List<QuestionSourceEntry> questions) {}

  private final PersonalQuizRepository data;
  private final ObjectMapper json;
  private final Clock clock;
  private final QuestionChoices choices;
  private final CategoryRepository categoryRepository;

  public PersonalQuizzes(
      PersonalQuizRepository data,
      ObjectMapper json,
      Clock clock,
      QuestionChoices choices,
      CategoryRepository categoryRepository) {
    this.data = data;
    this.json = json;
    this.clock = clock;
    this.choices = choices;
    this.categoryRepository = categoryRepository;
  }

  public List<Category> categories(UUID user) {
    return categoryRepository.list(user).stream()
        .map(c -> new Category(c.id(), c.name(), c.parentId()))
        .toList();
  }

  @Transactional
  public Category category(UUID user, String name) {
    return category(user, name, null);
  }

  @Transactional
  public Category category(UUID user, String name, UUID parentId) {
    lockUser(user);
    String clean = name.strip().replaceAll("\\s+", " ");
    String normalized = normalize(clean);
    if (parentId != null) new CategoryTree(categoryRepository.list(user)).descendants(parentId);
    var existing = categoryRepository.named(user, normalized);
    if (!existing.isEmpty() && !Objects.equals(existing.get(0).parentId(), parentId))
      throw bad("That category name already exists in another branch.");
    if (!existing.isEmpty()) {
      var found = existing.get(0);
      return new Category(found.id(), found.name(), found.parentId());
    }
    UUID id = UUID.randomUUID();
    categoryRepository.create(user, id, clean, normalized, parentId, now());
    return new Category(id, clean, parentId);
  }

  public List<Summary> list(UUID user) {
    return data.list(user).stream().map(this::summary).toList();
  }

  public Detail get(UUID user, UUID id) {
    return detail(owned(user, id, false));
  }

  @Transactional
  public Detail create(UUID user, Save input) {
    lockUser(user);
    if (input.version() != 0) throw bad("A new quiz must start at version 0.");
    validate(user, input);
    UUID id = UUID.randomUUID();
    var at = now();
    data.create(
        id,
        user,
        input.title().strip(),
        description(input),
        input.categoryId(),
        input.status(),
        write(input.questions()),
        at);
    if (input.status().equals("READY")) publish(user, id, input);
    return get(user, id);
  }

  @Transactional
  public Detail save(UUID user, UUID id, Save input) {
    lockUser(user);
    var row = owned(user, id, true);
    if (((Number) row.get("version")).intValue() != input.version())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This quiz was changed in another tab. Reload before saving.");
    validate(user, input);
    data.save(
        id,
        input.title().strip(),
        description(input),
        input.categoryId(),
        input.status(),
        write(input.questions()),
        now());
    data.unlink(id);
    if (input.status().equals("READY")) publish(user, id, input);
    return get(user, id);
  }

  @Transactional
  public Detail duplicate(UUID user, UUID id) {
    lockUser(user);
    Detail old = get(user, id);
    String title = old.quiz().title();
    if (title.length() > 113) title = title.substring(0, 113);
    return create(
        user,
        new Save(
            title + " (copy)",
            old.quiz().description(),
            old.quiz().categoryId(),
            old.questions(),
            0,
            "DRAFT"));
  }

  @Transactional
  public void delete(UUID user, UUID id) {
    lockUser(user);
    owned(user, id, true);
    data.retire(id, now());
  }

  public List<LibraryQuestion> library(UUID user) {
    return library(user, null);
  }

  public List<LibraryQuestion> library(UUID user, UUID categoryId) {
    List<UUID> branch =
        categoryId == null
            ? null
            : new CategoryTree(categoryRepository.list(user)).descendants(categoryId);
    return data.library(user, branch).stream()
        .map(
            row -> {
              var options = new ArrayList<>(choices.read((String) row.get("incorrect_answers")));
              options.add(0, (String) row.get("correct_answer"));
              if (row.get("question_type").equals("TRUE_FALSE"))
                options = new ArrayList<>(List.of("True", "False"));
              return new LibraryQuestion(
                  (UUID) row.get("id"),
                  (String) row.get("category_name"),
                  new Question(
                      (String) row.get("question_type"),
                      (String) row.get("text"),
                      options,
                      options.indexOf(row.get("correct_answer")),
                      null));
            })
        .toList();
  }

  /** Must be called inside room creation's transaction, after locking the host account. */
  public Playable playable(UUID user, UUID id) {
    var quiz = owned(user, id, true);
    if (!"READY".equals(quiz.get("status")))
      throw bad("Mark this quiz Ready to Play before hosting it.");
    var entries =
        data.playable(user, id).stream()
            .map(
                row ->
                    new QuestionSourceEntry(
                        (UUID) row.get("id"),
                        new BankQuestion(
                            (String) row.get("category_name"),
                            (String) row.get("text"),
                            (String) row.get("correct_answer"),
                            choices.read((String) row.get("incorrect_answers")),
                            (String) row.get("difficulty"),
                            (String) row.get("question_type"))))
            .toList();
    if (entries.isEmpty()) throw bad("This quiz has no playable questions.");
    return new Playable(id, ((Number) quiz.get("version")).intValue(), entries);
  }

  private void validate(UUID user, Save input) {
    if (input.categoryId() != null && !categoryRepository.owns(user, input.categoryId()))
      throw bad("Choose one of your own categories.");
    if (input.questions().stream().anyMatch(Objects::isNull)) throw bad("A question is missing.");
    for (int i = 0; i < input.questions().size(); i++) {
      Question q = input.questions().get(i);
      String prefix = "Question " + (i + 1) + ": ";
      if (q.type() == null || !Set.of("MULTIPLE_CHOICE", "TRUE_FALSE").contains(q.type()))
        throw bad(prefix + "choose Multiple Choice or True / False.");
      if (q.text() == null
          || q.text().length() > 4000
          || q.options() == null
          || q.options().size() > 6
          || q.options().stream().anyMatch(a -> a == null || a.length() > 1000))
        throw bad(
            prefix
                + "keep question text within 4,000 characters and each of up to 6"
                + " answers within 1,000 characters.");
      if (q.correctIndex() != null
          && (q.correctIndex() < 0 || q.correctIndex() >= q.options().size()))
        throw bad(prefix + "select an answer from the available options.");
      if (!input.status().equals("READY")) continue;
      if (q.text().isBlank()) throw bad(prefix + "enter question text.");
      if (q.options().size() < 2 || q.options().stream().anyMatch(String::isBlank))
        throw bad(prefix + "enter at least two nonblank answers.");
      if (q.options().stream().map(PersonalQuizzes::normalize).distinct().count()
          != q.options().size()) throw bad(prefix + "answer options must be different.");
      if (q.correctIndex() == null) throw bad(prefix + "select one correct answer.");
      if (q.type().equals("TRUE_FALSE") && !q.options().equals(List.of("True", "False")))
        throw bad(prefix + "True / False answers must be True and False.");
    }
    if (input.status().equals("READY") && input.questions().isEmpty())
      throw bad("Add at least one question before marking this quiz Ready to Play.");
  }

  private void publish(UUID user, UUID id, Save input) {
    String category =
        input.categoryId() == null
            ? "General Knowledge"
            : categoryRepository.name(user, input.categoryId());
    long categoryId = data.storageCategory(category, normalize(category));
    for (int i = 0; i < input.questions().size(); i++) {
      // The legacy game/analytics schema needs a value; it is not a custom quiz setting.
      Question q = input.questions().get(i);
      String storageDifficulty = "easy";
      String key =
          hash(
              user
                  + ":"
                  + categoryId
                  + ":"
                  + write(
                      List.of(
                          q.type(),
                          q.text().strip(),
                          q.options().stream().map(String::strip).sorted().toList(),
                          q.options().get(q.correctIndex()).strip(),
                          storageDifficulty)));
      var existing = data.existingQuestion(user, key);
      UUID questionId;
      if (existing.isEmpty()) {
        questionId = UUID.randomUUID();
        var wrong = new ArrayList<String>();
        for (int a = 0; a < q.options().size(); a++)
          if (a != q.correctIndex()) wrong.add(q.options().get(a).strip());
        data.question(
            questionId,
            user,
            categoryId,
            q.text().strip(),
            q.options().get(q.correctIndex()).strip(),
            choices.write(wrong),
            storageDifficulty,
            q.type(),
            key);
      } else questionId = existing.get(0);
      data.link(id, questionId, i);
    }
  }

  private Map<String, Object> owned(UUID user, UUID id, boolean lock) {
    var rows = data.owned(user, id, lock);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found.");
    return rows.get(0);
  }

  private Detail detail(Map<String, Object> r) {
    return new Detail(summary(r), read((String) r.get("draft_document")));
  }

  private Summary summary(Map<String, Object> r) {
    var qs = read((String) r.get("draft_document"));
    int mc = (int) qs.stream().filter(q -> q.type().equals("MULTIPLE_CHOICE")).count();
    return new Summary(
        (UUID) r.get("id"),
        (String) r.get("title"),
        (String) r.get("description"),
        (UUID) r.get("custom_category_id"),
        r.get("category_name") == null ? "General Knowledge" : (String) r.get("category_name"),
        (String) r.get("visibility"),
        (String) r.get("status"),
        ((Number) r.get("version")).intValue(),
        qs.size(),
        mc,
        qs.size() - mc,
        com.triviagame.online.DatabaseTimes.instant(r.get("created_at")),
        com.triviagame.online.DatabaseTimes.instant(r.get("updated_at")));
  }

  private void lockUser(UUID user) {
    if (!data.lockOwner(user)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  private List<Question> read(String value) {
    try {
      return json.readValue(value, new TypeReference<List<Question>>() {});
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Invalid saved quiz", e);
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String description(Save i) {
    return i.description() == null ? "" : i.description().strip();
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private static String normalize(String s) {
    return java.text.Normalizer.normalize(s.strip(), java.text.Normalizer.Form.NFKC)
        .replaceAll("\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  private static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ResponseStatusException bad(String text) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
  }
}
