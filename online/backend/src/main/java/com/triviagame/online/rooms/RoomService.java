package com.triviagame.online.rooms;

import com.triviagame.api.BankQuestion;
import com.triviagame.model.questions.*;
import com.triviagame.online.auth.AvatarConfig;
import com.triviagame.online.bank.OpenTriviaService;
import com.triviagame.online.bank.PersonalQuizzes;
import com.triviagame.online.bank.QuestionChoices;
import com.triviagame.online.bank.QuestionSourceEntry;
import com.triviagame.online.rooms.dto.RoomAnswerRequest;
import com.triviagame.online.rooms.dto.RoomAttempt;
import com.triviagame.online.rooms.dto.RoomCreateRequest;
import com.triviagame.online.rooms.dto.RoomParticipant;
import com.triviagame.online.rooms.dto.RoomQuestion;
import com.triviagame.online.rooms.dto.RoomRanking;
import com.triviagame.online.rooms.dto.RoomRetrySettings;
import com.triviagame.online.rooms.dto.RoomSummary;
import com.triviagame.online.rooms.dto.RoomView;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomService {
  private final IndependentGame independent;
  private final RoomRepository rooms;
  private final QuestionChoices questionChoices;
  private final OpenTriviaService trivia;
  private final ScoringService scoring;
  private final PersonalQuizzes quizzes;
  private final RoomDataRepository data;
  private final Clock clock;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;
  private final String publicUrl;
  private final Presence presence;
  private final SecureRandom random = new SecureRandom();

  public RoomService(
      RoomRepository rooms,
      QuestionChoices questionChoices,
      RoomDataRepository data,
      Clock clock,
      PasswordEncoder passwords,
      ApplicationEventPublisher events,
      @Value("${app.public-url}") String publicUrl,
      Presence presence,
      OpenTriviaService trivia,
      ScoringService scoring,
      PersonalQuizzes quizzes,
      IndependentGame independent) {
    this.independent = independent;
    this.rooms = rooms;
    this.questionChoices = questionChoices;
    this.data = data;
    this.clock = clock;
    this.passwords = passwords;
    this.events = events;
    this.publicUrl = publicUrl.replaceAll("/$", "");
    this.presence = presence;
    this.trivia = trivia;
    this.scoring = scoring;
    this.quizzes = quizzes;
  }

  /** Network work happens before the room transaction, never while holding an account lock. */
  public List<BankQuestion> prepare(RoomCreateRequest input) {
    if ("OPENTDB".equals(input.questionSource())) {
      if (input.quizId() != null
          || input.categoryId() != null
          || !Set.of("any", "multiple", "boolean").contains(input.questionType()))
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Choose valid Instant Quiz settings.");
      return trivia.fetch(
          input.questionCount(),
          input.openTdbCategoryId(),
          input.difficulty(),
          input.questionType());
    }
    if (!"LOCAL".equals(input.questionSource()) || input.quizId() == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose Instant Quiz or select a saved quiz from My Quizzes.");
    if (input.openTdbCategoryId() != null
        || input.categoryId() != null
        || !"any".equals(input.questionType()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Your saved quiz supplies its own questions and types.");
    return List.of();
  }

  private void snapshot(QuizRoom room, List<QuestionSourceEntry> entries) {
    var pool = new ArrayList<>(entries);
    if (pool.size() < room.questionCount)
      throw conflict("Not enough questions match. Choose fewer questions or broader filters.");
    if (room.customQuizId == null) Collections.shuffle(pool, random);
    for (int i = 0; i < room.questionCount; i++) {
      var entry = pool.get(i);
      var q = entry.question();
      var choices = new ArrayList<>(q.incorrectAnswers());
      choices.add(q.correctAnswer());
      Collections.shuffle(choices, random);
      data.snapshot(
          room.id,
          entry.questionId(),
          i,
          q.text(),
          q.correctAnswer(),
          questionChoices.write(choices),
          q.difficulty(),
          q.questionType(),
          scoring.maximum(
              room.scoringVersion,
              model(q.text(), q.correctAnswer(), choices, q.questionType()).getPoints()),
          q.category());
    }
  }

  @Transactional
  public RoomView create(UUID user, RoomCreateRequest input, List<BankQuestion> downloaded) {
    requireActiveAccount(user);
    int active = data.activeRooms(user, clock.instant());
    if (active >= 5) throw conflict("Close an existing room before creating another");
    QuizRoom r = new QuizRoom();
    r.id = UUID.randomUUID();
    r.code = code();
    r.hostId = user;
    r.title = input.title().strip();
    r.status = "LOBBY";
    r.questionSource = input.questionSource();
    r.independentFlow = true;
    r.revealAnswers = input.revealAnswers() == null || input.revealAnswers();
    r.openTdbCategoryId = input.openTdbCategoryId();
    r.difficulty = input.quizId() == null ? input.difficulty() : "any";
    r.questionType = input.questionType();
    r.questionCount = input.questionCount();
    r.secondsPerQuestion = input.secondsPerQuestion();
    r.maxPlayers = input.maxPlayers();
    r.retryAllowance = input.retryAllowance() == null ? 0 : input.retryAllowance();
    if (r.retryAllowance < 0 || r.retryAllowance > 3)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Retry chances must be Off, 1, 2 or 3");
    r.createdAt = clock.instant();
    r.expiresAt = r.createdAt.plus(Duration.ofHours(6));
    if (input.password() != null && !input.password().isBlank()) {
      if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room password is too long");
      r.passwordHash = passwords.encode(input.password());
    }
    List<QuestionSourceEntry> pool;
    if ("OPENTDB".equals(r.questionSource)) {
      pool = downloaded.stream().map(q -> new QuestionSourceEntry(null, q)).toList();
    } else {
      var quiz = quizzes.playable(user, input.quizId());
      r.customQuizId = quiz.id();
      r.customQuizVersion = quiz.version();
      pool = quiz.questions();
      r.questionCount = pool.size();
    }
    if (pool.size() < r.questionCount)
      throw conflict(
          "Not enough questions match. Choose fewer questions or broader filters, or"
              + " create a quiz in My Quizzes.");
    r = rooms.saveAndFlush(r);
    snapshot(r, pool);
    data.participant(r.id, user, "HOST", clock.instant());
    changed(r);
    return view(r, user);
  }

  @Transactional
  public RoomView join(String code, UUID user, String password) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    usable(r);
    if (member(r.id, user)) return view(r, user);
    if (!r.status.equals("LOBBY")) throw conflict("This room is no longer accepting players");
    if (r.passwordHash != null
        && !passwords.matches(password == null ? "" : password, r.passwordHash))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect room password");
    int count = data.participantCount(r.id);
    if (count >= r.maxPlayers + (r.independentFlow ? 1 : 0)) throw conflict("This room is full");
    if (!data.rejoin(r.id, user, clock.instant()))
      data.participant(r.id, user, "PLAYER", clock.instant());
    changed(r);
    return view(r, user);
  }

  @Transactional
  public RoomView get(String code, UUID user) {
    QuizRoom r = lock(code);
    // The host and users in the game's frozen roster retain results access after leaving.
    // A lobby visit alone must not grant access after the host removes that visitor.
    boolean historical =
        r.status.equals("FINISHED")
            && (r.hostId.equals(user) || data.hasHistoricalScore(r.id, user));
    if (!historical) requireMember(r, user);
    return view(r, user);
  }

  @Transactional(readOnly = true)
  public boolean mayConnect(String code, UUID user) {
    QuizRoom r = find(code);
    return member(r.id, user)
        && !r.status.equals("CLOSED")
        && clock.instant().isBefore(r.expiresAt);
  }

  public List<RoomSummary> list(UUID user) {
    return data.roomsFor(user).stream()
        .map(
            row ->
                new RoomSummary(
                    (String) row.get("code"),
                    (String) row.get("title"),
                    (String) row.get("status"),
                    com.triviagame.online.DatabaseTimes.instant(row.get("created_at")),
                    user.equals(row.get("host_id"))))
        .toList();
  }

  @Transactional
  public RoomView settings(String code, UUID user, RoomRetrySettings input) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireHost(r, user);
    usable(r);
    if (!r.status.equals("LOBBY")) throw conflict("Settings are locked after the game starts");
    if (input.retryAllowance() < 0 || input.retryAllowance() > 3)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Retry chances must be Off, 1, 2 or 3");
    r.retryAllowance = input.retryAllowance();
    r.scoringVersion = ScoringService.CURRENT;
    changed(r);
    return view(r, user);
  }

  @Transactional
  public RoomView start(String code, UUID user) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireHost(r, user);
    usable(r);
    if (!r.status.equals("LOBBY")) throw conflict("Quiz already started or closed");
    if (data.participantCount(r.id) < 2)
      throw conflict("Invite at least one player before starting");
    if (!data.hasCompleteQuestionSnapshots(r.id, r.questionCount))
      throw conflict("This room's questions are unavailable. Create a new room to play.");
    UUID session = UUID.randomUUID();
    if (!r.independentFlow) data.start(session, r.id, r.retryAllowance, clock.instant());
    r.currentQuestion = 0;
    if (r.independentFlow) independent.start(r);
    else beginQuestion(r);
    changed(r);
    return view(r, user);
  }

  @Transactional
  public RoomAttempt answer(String code, UUID user, RoomAnswerRequest answer) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireMember(r, user);
    if (r.independentFlow) {
      var result = independent.answer(r, user, answer);
      changed(r);
      return result;
    }
    var replay = data.replay(user, answer.submissionId());
    if (!replay.isEmpty()) {
      var old = replay.get(0);
      if (!r.id.equals(old.get("room_id"))
          || ((Number) old.get("ordinal")).intValue() != answer.questionNumber() - 1
          || ((Number) old.get("selected_index")).intValue() != answer.selectedIndex())
        throw conflict("Submission ID was already used for another answer");
      return attempt(old, r, ((Number) old.get("question_maximum")).intValue());
    }
    answerable(r, answer.questionNumber());
    Map<String, Object> q = current(r);
    List<String> choices = questionChoices.read((String) q.get("choices"));
    if (answer.selectedIndex() >= choices.size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown answer choice");
    UUID question = (UUID) q.get("id");
    if (data.finalized(question, user)) throw conflict("Your answer has already been submitted");
    var attempts = data.attempts(question, user);
    Instant now = clock.instant();
    if (!attempts.isEmpty()
        && now.isBefore(
            com.triviagame.online.DatabaseTimes.instant(
                    attempts.get(attempts.size() - 1).get("accepted_at"))
                .plusMillis(300)))
      throw conflict("Your previous answer is still settling. Please wait a moment");
    Question model =
        model(
            (String) q.get("text"),
            (String) q.get("correct_answer"),
            choices,
            (String) q.get("question_type"));
    boolean correct = model.checkAnswer(choices.get(answer.selectedIndex()));
    int balance = data.balances(r.id, user).get(0);
    String outcome = correct ? "CORRECT" : balance > 0 ? "RETRY" : "INCORRECT";
    if (outcome.equals("RETRY")) {
      balance--;
      data.balance(r.id, user, balance);
    }
    data.attempt(
        question,
        user,
        answer.submissionId(),
        attempts.size() + 1,
        answer.selectedIndex(),
        correct,
        outcome,
        balance,
        now);
    if (!outcome.equals("RETRY")) {
      boolean first =
          attempts.isEmpty() ? correct : Boolean.TRUE.equals(attempts.get(0).get("is_correct"));
      int used = (int) attempts.stream().filter(a -> "RETRY".equals(a.get("outcome"))).count();
      data.answer(
          question,
          user,
          answer.selectedIndex(),
          correct,
          correct
              ? scoring.correctPoints(r.scoringVersion, used, ((Number) q.get("points")).intValue())
              : 0,
          now,
          false,
          first,
          attempts.size() + 1,
          used,
          elapsed(r, now));
    }
    changed(r);
    return new RoomAttempt(
        answer.submissionId(),
        outcome,
        balance,
        now,
        outcome.equals("RETRY")
            ? scoring.correctPoints(
                r.scoringVersion, attempts.size() + 1, ((Number) q.get("points")).intValue())
            : 0,
        award(outcome, r.scoringVersion, attempts.size(), ((Number) q.get("points")).intValue()));
  }

  @Transactional
  public RoomView retry(
      String code, UUID user, com.triviagame.online.rooms.dto.RoomRetryDecision input) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireMember(r, user);
    if (!r.independentFlow) throw conflict("This legacy room uses its original retry rules.");
    independent.decide(r, user, input);
    changed(r);
    return view(r, user);
  }

  @Transactional
  public void undo(String code, UUID user, int questionNumber) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireMember(r, user);
    throw conflict("Online answers use automatic retry chances. Final answers cannot be undone");
  }

  @Transactional
  public RoomView next(String code, UUID user) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireHost(r, user);
    if (r.independentFlow) throw conflict("Players advance automatically after feedback.");
    if (!r.status.equals("REVEAL"))
      throw conflict("Wait for this question's deadline before advancing");
    advance(r);
    changed(r);
    return view(r, user);
  }

  @Transactional
  public RoomView end(String code, UUID user) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    requireHost(r, user);
    if (r.independentFlow && !Set.of("LOBBY", "CLOSED", "FINISHED").contains(r.status)) {
      independent.end(r);
      changed(r);
      return view(r, user);
    }
    if (r.status.equals("LOBBY")) r.status = "CLOSED";
    else if (r.status.equals("QUESTION") || r.status.equals("REVEAL")) {
      if (r.status.equals("QUESTION")) reveal(r);
      finish(r);
    }
    r.deadline = null;
    changed(r);
    return view(r, user);
  }

  @Transactional
  public void leave(String code, UUID user) {
    requireActiveAccount(user);
    QuizRoom r = lock(code);
    if (r.status.equals("FINISHED") && r.hostId.equals(user) && !member(r.id, user)) return;
    requireMember(r, user);
    if (r.hostId.equals(user) && !Set.of("CLOSED", "FINISHED").contains(r.status))
      throw conflict("Close or end your room before leaving");
    if (r.independentFlow && r.status.equals("QUESTION")) {
      independent.leave(r, user);
      data.leave(r.id, user, clock.instant());
      changed(r);
      return;
    }
    if (r.status.equals("QUESTION")
        && r.deadline != null
        && !clock.instant().isBefore(r.deadline)) {
      reveal(r);
    }
    if (Set.of("QUESTION", "REVEAL").contains(r.status)) {
      data.forfeit(r.id, user);
    }
    data.leave(r.id, user, clock.instant());
    changed(r);
  }

  @Transactional
  public void remove(String code, UUID host, UUID participant) {
    requireActiveAccount(host);
    QuizRoom r = lock(code);
    requireHost(r, host);
    if (!r.status.equals("LOBBY")) throw conflict("Participants can only be removed in the lobby");
    if (host.equals(participant)) throw conflict("The host cannot be removed");
    data.remove(r.id, participant, clock.instant());
    changed(r);
  }

  @Transactional
  public void tick(String code) {
    QuizRoom r = lock(code);
    if (r.independentFlow && r.status.equals("QUESTION")) {
      if (independent.tick(r)) changed(r);
      return;
    }
    if (r.status.equals("LOBBY") && !clock.instant().isBefore(r.expiresAt)) {
      r.status = "CLOSED";
      changed(r);
      return;
    }
    if ((r.status.equals("QUESTION") || r.status.equals("REVEAL"))
        && r.deadline != null
        && !clock.instant().isBefore(r.deadline)) {
      if (r.status.equals("QUESTION")) reveal(r);
      else advance(r);
      changed(r);
    }
  }

  @Transactional
  public void retireAccount(UUID user) {
    // Lock order is account -> rooms, shared with player commands. Anonymous
    // roster/scores remain to preserve the other players' legitimate results.
    requireActiveAccount(user);
    var codes = data.relatedRooms(user);
    for (String code : codes) {
      QuizRoom r = lock(code);
      if (r.independentFlow && r.status.equals("QUESTION")) {
        if (r.hostId.equals(user)) independent.end(r);
        else independent.leave(r, user);
      }
      if (r.hostId.equals(user)) {
        if (r.status.equals("LOBBY")) r.status = "CLOSED";
        else if (r.status.equals("QUESTION") || r.status.equals("REVEAL")) {
          if (r.status.equals("QUESTION")) reveal(r);
          finish(r);
        }
        r.deadline = null;
        r.title = "Quiz room";
        r.passwordHash = null;
      }
      if (!r.hostId.equals(user) && Set.of("QUESTION", "REVEAL").contains(r.status)) {
        data.forfeit(r.id, user);
      }
      data.leave(r.id, user, clock.instant());
      changed(r);
    }
  }

  private void requireActiveAccount(UUID user) {
    if (!data.lockActiveAccount(user))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to continue");
  }

  private void reveal(QuizRoom r) {
    // Serialize this transition with submissions; score totals are hidden until the reveal.
    UUID session = data.session(r.id);
    // Only new rulesets have complete timeout/attempt records. Never guess legacy history.
    if (!r.scoringVersion.equals("legacy-v1")) {
      UUID question = (UUID) current(r).get("id");
      for (UUID player : data.eligiblePlayers(session)) {
        if (data.finalized(question, player)) continue;
        var attempts = data.attempts(question, player);
        Boolean first =
            attempts.isEmpty() ? null : Boolean.TRUE.equals(attempts.get(0).get("is_correct"));
        int used = (int) attempts.stream().filter(a -> "RETRY".equals(a.get("outcome"))).count();
        Instant at = clock.instant().isBefore(r.deadline) ? clock.instant() : r.deadline;
        data.answer(
            question,
            player,
            null,
            false,
            0,
            at,
            true,
            first,
            attempts.size(),
            used,
            elapsed(r, at));
      }
    }
    for (Map<String, Object> answer : data.roundAnswers(r.id, r.currentQuestion)) {
      data.addScore(
          session,
          (UUID) answer.get("user_id"),
          ((Number) answer.get("points")).intValue(),
          Boolean.TRUE.equals(answer.get("is_correct")));
    }
    r.status = "REVEAL";
    r.deadline = clock.instant().plusSeconds(5);
  }

  private void advance(QuizRoom r) {
    if (r.currentQuestion + 1 >= r.questionCount) {
      finish(r);
      return;
    }
    r.currentQuestion++;
    beginQuestion(r);
  }

  private void beginQuestion(QuizRoom r) {
    r.status = "QUESTION";
    r.questionStartedAt = clock.instant();
    r.deadline = r.questionStartedAt.plusSeconds(r.secondsPerQuestion);
    data.presented(r.id, r.currentQuestion, r.questionStartedAt);
  }

  private long elapsed(QuizRoom r, Instant at) {
    return Math.max(
        0,
        Duration.between(
                r.questionStartedAt == null
                    ? r.deadline.minusSeconds(r.secondsPerQuestion)
                    : r.questionStartedAt,
                at)
            .toMillis());
  }

  private Integer award(String outcome, String version, int retries, int maximum) {
    if (outcome.equals("CORRECT")) return scoring.correctPoints(version, retries, maximum);
    return outcome.equals("INCORRECT") ? Integer.valueOf(0) : null;
  }

  private RoomAttempt attempt(Map<String, Object> a, QuizRoom room, int maximum) {
    String outcome = (String) a.get("outcome");
    int number = ((Number) a.get("attempt_number")).intValue();
    return new RoomAttempt(
        (UUID) a.get("submission_id"),
        outcome,
        ((Number) a.get("retries_remaining")).intValue(),
        com.triviagame.online.DatabaseTimes.instant(a.get("accepted_at")),
        outcome.equals("RETRY") ? scoring.correctPoints(room.scoringVersion, number, maximum) : 0,
        award(outcome, room.scoringVersion, number - 1, maximum));
  }

  private void finish(QuizRoom r) {
    r.status = "FINISHED";
    r.deadline = null;
    data.finish(r.id, clock.instant());
  }

  private RoomView view(QuizRoom r, UUID user) {
    if (r.independentFlow) return independent.view(r, user, publicUrl, presence);
    List<RoomParticipant> players =
        data.participants(r.id).stream()
            .map(
                row -> {
                  UUID id = (UUID) row.get("user_id");
                  return new RoomParticipant(
                      id,
                      (String) row.get("display_name"),
                      (String) row.get("role"),
                      presence.isConnected(r.code, id),
                      row.get("left_at") != null,
                      AvatarConfig.read((String) row.get("avatar_config"), id),
                      (String) row.get("username"));
                })
            .toList();
    List<RoomRanking> rankings =
        data.rankings(r.id).stream()
            .map(
                row -> {
                  UUID id = (UUID) row.get("id");
                  return new RoomRanking(
                      id,
                      (String) row.get("display_name"),
                      ((Number) row.get("points")).intValue(),
                      ((Number) row.get("correct_count")).intValue(),
                      ((Number) row.get("ranking")).intValue(),
                      AvatarConfig.read((String) row.get("avatar_config"), id),
                      Boolean.TRUE.equals(row.get("forfeited")),
                      (String) row.get("username"));
                })
            .toList();
    RoomQuestion question = null;
    Integer selected = null;
    boolean finalized = false;
    RoomAttempt latest = null;
    int available = 0;
    Integer awarded = null;
    if (Set.of("QUESTION", "REVEAL").contains(r.status)) {
      Map<String, Object> q = current(r);
      question =
          new RoomQuestion(
              r.currentQuestion + 1,
              (String) q.get("text"),
              questionChoices.read((String) q.get("choices")),
              (String) q.get("difficulty"),
              (String) q.get("question_type"),
              r.status.equals("REVEAL") ? (String) q.get("correct_answer") : null);
      var answers = data.submitted((UUID) q.get("id"), user);
      if (!answers.isEmpty()) {
        selected = (Integer) answers.get(0).get("selected_index");
        finalized = true;
        awarded = ((Number) answers.get(0).get("points")).intValue();
      }
      var recent = data.attempts((UUID) q.get("id"), user);
      if (!recent.isEmpty())
        latest = attempt(recent.get(recent.size() - 1), r, ((Number) q.get("points")).intValue());
      if (!finalized && r.status.equals("QUESTION"))
        available =
            latest == null ? ((Number) q.get("points")).intValue() : latest.pointsAvailable();
    }
    var balances = data.balances(r.id, user);
    return new RoomView(
        r.code,
        r.title,
        r.status,
        r.hostId,
        publicUrl + "/join/" + r.code,
        r.maxPlayers,
        r.questionCount,
        r.secondsPerQuestion,
        r.deadline,
        clock.instant(),
        players,
        rankings,
        question,
        selected,
        r.retryAllowance,
        balances.isEmpty() ? r.retryAllowance : balances.get(0),
        finalized,
        latest,
        available,
        awarded,
        r.scoringVersion,
        r.questionSource,
        r.openTdbCategoryId,
        r.difficulty,
        r.questionType,
        r.customQuizId,
        r.customQuizVersion,
        false,
        r.revealAnswers,
        null,
        null,
        false,
        0,
        List.of());
  }

  private void answerable(QuizRoom r, int number) {
    if (!r.status.equals("QUESTION")
        || number != r.currentQuestion + 1
        || r.deadline == null
        || !clock.instant().isBefore(r.deadline))
      throw conflict("This question is no longer accepting answers");
  }

  private Map<String, Object> current(QuizRoom r) {
    return data.current(r.id, r.currentQuestion);
  }

  private boolean member(UUID room, UUID user) {
    return data.member(room, user);
  }

  private void requireMember(QuizRoom r, UUID user) {
    if (!member(r.id, user)) {
      if (data.removedByHost(r.id, user)) throw new RoomRemovedException();
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Join this room before accessing it");
    }
  }

  private void requireHost(QuizRoom r, UUID user) {
    if (!r.hostId.equals(user))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the room host can do that");
  }

  private void usable(QuizRoom r) {
    if (!clock.instant().isBefore(r.expiresAt) || Set.of("CLOSED", "FINISHED").contains(r.status))
      throw conflict("This room is closed or expired");
  }

  private QuizRoom lock(String code) {
    return rooms
        .lockByCode(normalizeCode(code))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Room not found. Check your code"));
  }

  private QuizRoom find(String code) {
    return rooms
        .findByCode(normalizeCode(code))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Room not found. Check your code"));
  }

  private String normalizeCode(String code) {
    String normalized = code.strip().toUpperCase(Locale.ROOT);
    if (!normalized.matches("[A-Z2-9]{8}"))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Enter an eight-character room code");
    return normalized;
  }

  private String code() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 8; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
    return code.toString();
  }

  private void changed(QuizRoom r) {
    events.publishEvent(new RoomChanged(r.code));
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private static Question model(String text, String correct, List<String> choices, String type) {
    return type.equals("TRUE_FALSE")
        ? new TrueFalseQuestion(text, correct)
        : new MultipleChoiceQuestion(text, choices.toArray(String[]::new), correct);
  }
}
