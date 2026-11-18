# Hootly — Project Design Document V1.0

CPE 314 Software Design · Section 3A · Team 02 · 18 November 2026

This document describes Hootly Online, the final product on `project/milestone-3`. The retained desktop application uses its own SQLite store for academic demonstrations. The schema sources define the columns, constraints, migrations, and indexes shown in the diagrams.

## 1. Introduction and project overview

In Hootly Online, hosts create rooms with either Instant Quiz, which fetches fresh OpenTDB questions, or My Quizzes, which uses a ready quiz from the host’s private collection. Players join, answer timed questions, and review results. Users can export statistics and compare imported histories. A recipient must consent before a comparison can update from their live account.

## 2. ER sketch and team roles

The current entity relationships are shown in Section 4. Team roles below are the roles recorded in the existing project document and instructor review, not a claim that each person authored every current file. Each member should verify and explain their own final contributions at defense.

| Member | Documented role |
|---|---|
| John Daevin S. Simbajon | Team lead, integration and DevOps |
| Clarence Philip A. Elegino | Core object-oriented design and game logic |
| Justine B. Cuizon | Database, data and API |
| Joshua Neil G. Malaya | UI/UX and JavaFX |
| Gebrielle P. Mirasol | Analytics and QA |

## 3. System architecture and object-oriented design

```mermaid
flowchart LR
  Browser[React / Vite browser] -->|HTTPS API and WebSocket via Vite proxy| Web[Spring Boot controllers]
  Web --> Services[Auth, rooms, quizzes, practice and analytics services]
  Services --> Repos[JDBC repositories and JPA]
  Repos --> PG[(PostgreSQL / Flyway)]
  Services --> Room[Room creation and snapshot persistence]
  Room --> Instant[Instant Quiz]
  Instant --> OpenTDB[Open Trivia Database]
  Room --> Saved[My Quizzes]
  Saved --> Repos
  Room --> Repos
  Services --> Mail[Configured email transport]
```

`online/backend/src/main/java/com/triviagame/online` separates web controllers, domain services and repositories. The backend build uses selected shared Java API and question model classes. The retained academic desktop entry point is `com.triviagame.App`. Its JavaFX screens, services, and DAOs use a separate SQLite file. `GameController` mediates game actions and `GameSession` still constructs some DAOs.

```mermaid
classDiagram
  class Question { <<abstract>> +checkAnswer(input) +getPoints() +getChoices() }
  class MultipleChoiceQuestion
  class TrueFalseQuestion
  class CategoryQuestion { <<abstract>> -Question question }
  class ScienceQuestion
  class HistoryQuestion
  class EntertainmentQuestion
  Question <|-- MultipleChoiceQuestion
  Question <|-- TrueFalseQuestion
  Question <|-- CategoryQuestion
  CategoryQuestion <|-- ScienceQuestion
  CategoryQuestion <|-- HistoryQuestion
  CategoryQuestion <|-- EntertainmentQuestion
  CategoryQuestion --> Question : delegates answer format
```

Category decorators retain multiple-choice or true/false answer behavior while dynamically supplying category-specific scoring. `GameEngine` uses `ArrayDeque` as a FIFO question queue and as a LIFO scored-answer history. `NewGameScreen` exposes Undo. `Category.getAllQuestions` and `CategoryDao.getQuestionsRecursively` traverse nested categories; stable IDs and maps distinguish players with identical names.

## 4. Data model and database design

### PostgreSQL online ERD

Flyway migrations V1 through V16 define the schema. SQL migrations live in `online/backend/src/main/resources/db/migration/` and Java migrations in `online/backend/src/main/java/db/migration/`. The `room_questions` table contains the room snapshots. Instant Quiz snapshots use a null `question_id` and record their provider category. My Quizzes snapshots reference the saved question by ID and copy the playable contents. Older shared questions and their category links remain available to historical games and analytics.

```mermaid
erDiagram
  USERS ||--o{ QUIZ_ROOMS : hosts
  USERS ||--o{ ROOM_PARTICIPANTS : joins
  QUIZ_ROOMS ||--o{ ROOM_PARTICIPANTS : includes
  QUIZ_ROOMS ||--o| GAME_SESSIONS : starts
  QUIZ_ROOMS ||--o{ ROOM_QUESTIONS : presents
  CATEGORIES o|--o{ QUIZ_ROOMS : historical_filter
  CATEGORIES o|--o{ CATEGORIES : parent_of
  CATEGORIES ||--o{ QUESTIONS : categorizes
  QUESTIONS o|--o{ ROOM_QUESTIONS : saved_or_historical_source
  USERS ||--o{ ANSWERS : submits
  ROOM_QUESTIONS ||--o{ ANSWERS : receives
  GAME_SESSIONS ||--o{ SCORES : contains
  USERS ||--o{ SCORES : earns
  USERS ||--o{ QUIZ_COLLECTIONS : owns
  QUIZ_COLLECTIONS ||--o{ QUIZ_COLLECTION_QUESTIONS : contains
  QUESTIONS ||--o{ QUIZ_COLLECTION_QUESTIONS : included
  USERS ||--o{ COMPARED_PLAYERS : owns
  COMPARED_PLAYERS ||--o{ COMPARED_GAMES : stores
  USERS ||--o{ STATISTICS_SHARING : requests
  USERS ||--o{ STATISTICS_SHARING : receives
  USERS { UUID id PK
    string username UK
    string email UK }
  QUIZ_ROOMS { UUID id PK
    UUID host_id FK
    string code UK
    string question_source
    int opentdb_category_id
    long category_id FK
    UUID custom_quiz_id FK }
  GAME_SESSIONS { UUID id PK
    UUID room_id FK,UK }
  ROOM_PARTICIPANTS { UUID room_id PK,FK
    UUID user_id PK,FK }
  ROOM_QUESTIONS { UUID id PK
    UUID room_id FK
    UUID question_id FK }
  ANSWERS { UUID id PK
    UUID room_question_id FK
    UUID user_id FK }
  SCORES { UUID session_id PK,FK
    UUID user_id PK,FK }
  COMPARED_PLAYERS { UUID id PK
    UUID owner_id FK
    UUID verified_user_id FK }
  COMPARED_GAMES { UUID member_id PK,FK
    string game_id PK }
  STATISTICS_SHARING { UUID id PK
    UUID requester_id FK
    UUID recipient_id FK
    string status }
```

`answer_attempts`, `room_retry_decisions`, `room_player_progress`, and `room_question_presentations` track retry decisions, independent timing and presentation. Email challenges, delivery tracking, import jobs and Spring Session tables are separate support records. PostgreSQL global category normalized names are unique. Personal categories support parents, but V7 retains an owner-wide normalized-name uniqueness constraint even after V13 adds parents; names cannot currently repeat under different parents for the same owner. The Flyway files are the exact migration history.

Additional PostgreSQL relationships (the FK from `ROOM_RETRY_DECISIONS` to `ANSWER_ATTEMPTS` uses the unique pair `user_id, submission_id`):

```mermaid
erDiagram
  USERS ||--o{ PERSONAL_QUIZ_CATEGORIES : owns
  PERSONAL_QUIZ_CATEGORIES o|--o{ PERSONAL_QUIZ_CATEGORIES : parent_of
  PERSONAL_QUIZ_CATEGORIES o|--o{ QUIZ_COLLECTIONS : groups
  QUIZ_COLLECTIONS o|--o{ QUIZ_ROOMS : source_for
  USERS o|--o{ QUESTIONS : owns
  QUIZ_ROOMS ||--o{ ROOM_PLAYER_PROGRESS : tracks
  USERS ||--o{ ROOM_PLAYER_PROGRESS : progresses
  ROOM_QUESTIONS ||--o{ ROOM_QUESTION_PRESENTATIONS : shown
  USERS ||--o{ ROOM_QUESTION_PRESENTATIONS : sees
  ROOM_QUESTIONS ||--o{ ANSWER_ATTEMPTS : receives
  USERS ||--o{ ANSWER_ATTEMPTS : submits
  ANSWER_ATTEMPTS ||--o| ROOM_RETRY_DECISIONS : decision
  USERS ||--o{ STATISTICS_IMPORT_JOBS : owns
  USERS o|--o{ COMPARED_PLAYERS : verified_account
  USERS o|--o{ QUESTION_BANK_IMPORTS : starts
  USERS ||--o{ QUIZ_PRACTICE_SESSIONS : owns
  QUIZ_COLLECTIONS ||--o{ QUIZ_PRACTICE_SESSIONS : practices
  USERS o|--o{ ACCOUNT_EMAIL_CHALLENGES : receives
  USERS ||--o{ PASSWORD_RESET_GRANTS : holds
  USERS o|--o{ ACCOUNT_EMAIL_DELIVERIES : receives
  ACCOUNT_EMAIL_CHALLENGES o|--o{ ACCOUNT_EMAIL_DELIVERIES : causes
  SPRING_SESSION ||--o{ SPRING_SESSION_ATTRIBUTES : contains
  ROOM_PLAYER_PROGRESS { UUID room_id PK,FK
    UUID user_id PK,FK }
  ROOM_QUESTION_PRESENTATIONS { UUID room_question_id PK,FK
    UUID user_id PK,FK }
  ANSWER_ATTEMPTS { UUID id PK
    UUID room_question_id FK
    UUID user_id FK
    UUID submission_id }
  ROOM_RETRY_DECISIONS { UUID user_id PK,FK
    UUID submission_id PK,FK }
  PERSONAL_QUIZ_CATEGORIES { UUID id PK
    UUID owner_user_id FK
    UUID parent_id FK }
  QUIZ_PRACTICE_SESSIONS { UUID id PK
    UUID owner_id FK
    UUID quiz_id FK }
  ACCOUNT_EMAIL_CHALLENGES { UUID id PK
    UUID user_id FK }
  ACCOUNT_EMAIL_DELIVERIES { UUID id PK
    UUID user_id FK
    UUID challenge_id FK }
  PASSWORD_RESET_GRANTS { UUID id PK
    UUID user_id FK }
  STATISTICS_IMPORT_JOBS { UUID id PK
    UUID owner_id FK }
  QUESTION_BANK_IMPORTS { UUID id PK
    UUID user_id FK }
  SPRING_SESSION { string PRIMARY_ID PK }
  SPRING_SESSION_ATTRIBUTES { string SESSION_PRIMARY_ID PK,FK
    string ATTRIBUTE_NAME PK }
```

`opentdb_category_cache` stores the provider’s category list. `analytics_signing_keys`, `opentdb_category_cache`, and `import_mutex` have no foreign keys. My Quizzes still uses `import_mutex` to serialize creation of storage categories. The former shared bank import log remains for historical compatibility. `COMPARED_GAMES.game_id` identifies imported history independently of local sessions.

### SQLite ERD for the retained academic application

The core schema and preservation-oriented migration are in `src/main/java/com/triviagame/db/Database.java`; session membership and timing are created by `SessionPlayerDao` and `TimedAnswerDao`. `QuestionBankService` creates `question_bank_imports` independently, without the relationships below.

```mermaid
erDiagram
  CATEGORIES o|--o{ CATEGORIES : parent_of
  CATEGORIES ||--o{ QUESTIONS : categorizes
  GAME_SESSIONS ||--o{ SESSION_PLAYERS : includes
  PLAYERS ||--o{ SESSION_PLAYERS : participates
  GAME_SESSIONS ||--o{ SCORES : contains
  PLAYERS ||--o{ SCORES : earns
  QUESTIONS ||--o{ SCORES : determines_category
  GAME_SESSIONS ||--o{ TIMED_ANSWERS : records
  PLAYERS ||--o{ TIMED_ANSWERS : submits
  QUESTIONS ||--o{ TIMED_ANSWERS : times
  CATEGORIES { int id PK
    int parent_id FK
    string name }
  QUESTIONS { int id PK
    int category_id FK
    string text
    string correct_answer
    string incorrect_answers
    string difficulty
    string question_type
    string external_id UK
    string created_at }
  PLAYERS { int id PK
    string player_sync_id UK
    string name }
  GAME_SESSIONS { int id PK
    string played_at
    string session_sync_id UK }
  SESSION_PLAYERS { int session_id PK,FK
    int player_id PK,FK
    int turn_order
    int seconds_per_question }
  SCORES { int id PK
    int session_id FK
    int player_id FK
    int question_id FK
    int is_correct
    int points
    string score_sync_id UK }
  TIMED_ANSWERS { int session_id PK,FK
    int player_id PK,FK
    int question_id PK,FK
    int elapsed_ms
    int timed_out }
  QUESTION_BANK_IMPORTS { int id PK
    string source
    int inserted_count
    string imported_at }
```

Scores derive category from their question. The migration preserves stable IDs and checks for conflicts; it deliberately stops instead of discarding inconsistent timed-answer history. Existing duplicate sibling categories are retained; triggers prevent new case-insensitive duplicates under the same parent. Player names need not be unique because permanent player IDs distinguish identities.

## 5. API integration

`OpenTdbClient` requests `https://opentdb.com/api.php` with amount and applicable category, difficulty, type and encoding parameters. DTOs (`api/dto`), `OpenTdbResponseParser`, and `OpenTdbQuestionMapper` parse and map JSON, including encoded answer text. Instant Quiz reports provider errors or insufficient results and prevents creation of an incomplete room. My Quizzes loads the owner’s saved questions in quiz order. JSON question import into a private draft remains available through the quiz API.

```mermaid
sequenceDiagram
  participant UI as Host interface
  participant S as Room service
  participant API as OpenTDB
  participant DB as PostgreSQL
  UI->>S: Create room with selected source
  alt Instant Quiz
    S->>API: HTTPS GET with requested filters
    API-->>S: JSON response or provider error
    S->>S: Decode and validate the response
    break Provider error or insufficient questions
      S-->>UI: Report error
    end
  else My Quizzes
    S->>DB: Read the host’s ready quiz and its ordered questions
    break Quiz unavailable
      S-->>UI: Report error
    end
  end
  S->>DB: Save room and question snapshots together
  S-->>UI: Room code
```

Online REST routes include `/api/auth`, `/api/rooms`, `/api/analytics` and `/api/quizzes`; room events use `/ws/rooms/{code}`. `AnalyticsExport`, `SnapshotValidation`, `ExportSigning`, and `Comparisons` preserve import identity signatures, duplicate detection and conflicts. Mailjet REST or the configured SMTP provider delivers account messages; test profiles use captured/mocked delivery. Acceptance by a mail provider is not a delivery receipt.

## 6. Concurrency and multiplayer

`App` owns a desktop executor and shuts it down; JavaFX `Task` completion handlers update controls on the FX thread. Import and download work, setup counts, player loading and analytics queries are scheduled in background tasks. The desktop score importer remains synchronous and reports progress to its caller. `GameSession`/`TurnTimer` own desktop timing; fake-clock tests exercise boundaries.

The online backend uses server-side room locking, `IndependentGame` progress/deadlines, `GameScheduler`, idempotent submission IDs and WebSocket notifications. Client countdowns are displays, not the authority. A five-second retry decision pauses the player's question clock; a bounded delivery grace handles near-deadline submissions. Feedback ends before the next question starts. Account delivery and statistics import use bounded workers, and application lifecycle hooks shut them down.

```mermaid
flowchart TD
  A[Host creates room] --> B[Players join]
  B --> C[Host starts countdown]
  C --> D[Server starts each question clock]
  D --> E[Player submits answer]
  E --> F{Correct or final?}
  F -->|Wrong, chance available| G[Pause clock; choose within 5 seconds]
  G -->|Use chance| D
  G -->|Decline or decision expires| H[Finalize and show feedback]
  F -->|Correct or no chance| H
  D -->|Deadline| H
  H --> I{More questions?}
  I -->|Yes| D
  I -->|No| J[Final standings and personal results]
```

A real device/network demonstration is still required; unit tests cannot prove all public-forwarding and provider behavior.

## 7. Data mining and visualization

Desktop `AnalyticsDao` aggregates persisted scores by player ID, category and session; `AnalyticsScreen` displays labeled bar and line charts. Online `AnalyticsRepository` exports completed stored games and finalized answers; frontend analytics calculate category accuracy, point trends and comparison rankings from those records. Charts use labeled axes and do not fabricate data. A disconnected comparison keeps its imported JSON history but stops live reads; a renewed request requires fresh recipient consent.

## 8. Testing summary

The online Maven tests use the H2 test profile and disposable PostgreSQL Testcontainers. The source tests cover empty-database Instant Quiz creation, saved-quiz ownership, and continued play and export from a historical room snapshot. Frontend verification uses Vitest, Playwright, and the production build. See `doc/KNOWN_ISSUES.md` for test results and requirement evidence. The retained desktop suite previously completed 188 tests with zero failures, errors, or skips on 18 November 2026.

## 9. Individual contributions

The recorded roles are in Section 2. The Week 11 instructor review references contributions across all five members, but its commit counts describe an older reviewed revision, not this final tree. Avoid presenting those historical numbers as current. Each member must verify their own contribution, describe representative files and demonstrate their understanding during Q&A. Team members should confirm any more specific attribution before submission.
