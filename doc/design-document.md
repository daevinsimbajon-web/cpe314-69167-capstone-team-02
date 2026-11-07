# CPE 314 — Software Design
**University of Cebu**  
**Computer Engineering Department**

## Trivia & Quiz Night Manager
### Project Design Document

### Table 1: Project Information

| Information | Details |
| :--- | :--- |
| **Group Name / No.:** | 2 |
| **Members:** | **John Daevin S. Simbajon** - (Team Lead + Integrator/DevOps)<br>**Clarence Philip A. Elegino** - (Core OOP/Logic Developer)<br>**Justine B. Cuizon** - (Database Admin + Data Developer + API)<br>**Joshua Neil G. Malaya** - (UI/UX Designer + JavaFX Developer)<br>**Gebrielle P. Mirasol** - (Analytics + QA) |
| **Instructor:** | Richyll Son |
| **Term:** | First Semester, S.Y. 2026-2027 |
| **Document Version / Date:** | V0.3 - 11/05/26 |

---

## Table of Contents
- [Revision History](#revision-history)
- [1. Introduction & Project Overview](#1-introduction--project-overview)
- [2. Preliminary ER Sketch & Team Roles](#2-preliminary-er-sketch--team-roles)
- [3. System Architecture & Object-Oriented Design](#3-system-architecture--object-oriented-design)
- [4. Data Model & Database Design](#4-data-model--database-design)
- [5. API Integration Design](#5-api-integration-design)
- [6. Concurrency Design](#6-concurrency-design)

---
### Table 2: Revision History

## Revision History
*Update this table each week as your team adds or revises sections of this document.*

| Version | Date | Authors | Changes |
| :--- | :--- | :--- | :--- |
| **0.1** | 10/24/26 - 10/26/26 | Justine Cuizon, Clarence Philip Elegino, Joshua Malaya, Gebrielle Mirasol, John Daevin Simbajon | Initial draft — Week 8 sections (Section 1 to 2). Removing Unnecessary Instruction. Generating the Table of Contents. |
| **0.2** | 10/28/26 | John Daevin Simbajon | Week 9 sections added. |
| **0.3** | 11/05/26<br>11/06/26 | Gebrielle Mirasol<br>John Daevin Simbajon | Week 10 sections added (Sections 5 and 6). Sections 1, 2, 3 and 4 revised to match the implemented code: real package and class names, real database schema, JSON score import instead of CSV, corrected team roles table. <br> Revised One-Paragraph Pitch |
| **1.0** | *Pending* | Group 2 | Final compilation and polish — Week 11. |

---

## 1. Introduction & Project Overview

**Trivia & Quiz Game Night Manager** is an offline-first Java desktop application designed for local multiplayer trivia games. Players can register their names, choose a category, and take turns answering questions using the same device. The game uses a Queue to manage question order and a Stack to support undo functionality. SQLite stores questions, players, game sessions, and scores locally. OpenTDB is an optional source for downloading additional trivia questions, which can be saved for offline use. The system also supports score tracking, analytics, and visualizations to help participants review their performance across games.


- **Theme:** Custom idea (an entertainment and social-gaming tool for trivia nights).
- **Problem solved:** Quiz hosts usually keep score on paper or in scattered spreadsheets, so results are easy to lose and hard to compare from one night to the next.
- **Key value proposition:** One app that fetches questions, scores each player, stores the history, and turns it into clear charts, so groups can see who is improving and which categories are hardest.

---

## 2. Preliminary ER Sketch & Team Roles

### Preliminary ER Sketch
*(Figure 1: Entities, Key Attributes and Relationships — Week 8 proposal, superseded by Figure 3 in Section 4)*

```
+------------------+          +------------------------+          +-------------------+
|     CATEGORY     |          |        QUESTION        |          |   GAME_SESSION    |
+------------------+          +------------------------+          +-------------------+
| PK id            |<---+     | PK id                  |          | PK id             |
| UK name          |    |     | UK text                |          |    played_at      |
| FK parent_id     |+---+     |    correct_answer      |          +---------+---------+
+--------+---------+          |    incorrect_answers   |                    |
         |                    |    difficulty          |                    |
         |                    | FK category_id         |                    |
         |                    +-----------+------------+                    |
         |                                |                                 |
         +-------------------+            |            +--------------------+
                             |            |            |
                             v            v            v
                      +------------------------------------+      +-------------------+
                      |               SCORE                |      |      PLAYER       |
                      +------------------------------------+      +-------------------+
                      | PK id                              |      | PK id             |
                      | FK session_id                      |      | UK name           |
                      | FK player_id                       |<-----+-------------------+
                      | FK category_id                     |
                      | FK question_id                     |
                      |    is_correct                      |
                      |    points                          |
                      +------------------------------------+
```

### Table 3: Team members and primary roles
| Member | Primary Role |
| :--- | :--- |
| **John Daevin S. Simbajon** | Team Lead + Integrator/DevOps |
| **Clarence Philip A. Elegino** | Core OOP/Logic Developer |
| **Justine B. Cuizon** | Database Admin + Data Developer + API |
| **Joshua Neil G. Malaya** | UI/UX Designer + JavaFX Developer |
| **Gebrielle P. Mirasol** | Analytics + QA |

---

## 3. System Architecture & Object-Oriented Design

### Class Hierarchy
*(Figure 2: Class hierarchy)*

```
        +------------------------------------------------------+
        |                <<abstract>> Question                 |
        |------------------------------------------------------|
        | # id, categoryId, text, answer                       |
        |------------------------------------------------------|
        | + getText(), getId(), getCategoryId()               |
        | + getCorrectAnswer(): String                         |
        | + checkAnswer(String): boolean   (case-insensitive)  |
        | + <<abstract>> getPoints(): int                      |
        | + <<abstract>> getChoices(): List<String>            |
        | + <<abstract>> display(): void                       |
        +---------------------------+--------------------------+
                                    ^
                +-------------------+--------------------+
                |                                        |
+---------------+-----------------+      +---------------+-----------------+
|      MultipleChoiceQuestion     |      |        TrueFalseQuestion        |
|---------------------------------|      |---------------------------------|
| choices = correct + wrong,      |      | choices = ["True", "False"]     |
|   shuffled once                 |      | getPoints() = 5                 |
| getPoints() = 10                |      | checkAnswer: inherited          |
| checkAnswer: choice text, then  |      +---------------------------------+
|   1-based position              |
+---------------------------------+
```

The GUI and `GameEngine` only ever hold a `Question`. Each question type decides its own points, answer choices, and answer checking, so adding a new type (for example a numeric-answer question) means adding one subclass and one case in `QuestionMapper`, with no change to the game or the screens.

### Supporting Classes by Package

All packages are under `com.triviagame`.

- `questions`: Question, MultipleChoiceQuestion, TrueFalseQuestion, QuestionMapper
- `category`: Category
- `db`: Database, PlayerDao, CategoryDao, QuestionDao, ScoreDao, AnalyticsDao, SampleQuestionSeeder, ScoreJsonImporter, QuestionBankService, SessionPlayerDao, TimedAnswerDao
- `api`: OpenTdbClient, QuestionParser, BankQuestion
- `tasks`: FetchQuestionsTask, JsonImportTask
- `gameplay`: GameEngine, NewGameScreen, GameScreen
- `m2`: RoundRobinGame, M2GameScreen, M2DemoApp, M2Launcher
- `analytics`: AnalyticsScreen
- `results`: Results
- `importer`: ImportScreen
- `players`: Players
- `home`: Home, Navigator, Ui
- Root package: App, Main

### Table 4 : How Each Class Follows the Single Responsibility Principle

| Class or Component | Responsibility |
| :--- | :--- |
| Question and subclasses | Represent questions, scoring rules, and answer choices |
| QuestionMapper | Convert stored questions into the appropriate Question subclass |
| Category | Provide hierarchical category searching through recursion |
| Database | Establish SQLite connections and initialize the schema |
| DAO classes | Handle SQLite operations using PreparedStatement |
| GameEngine | Manage question ordering, scores, and Undo |
| OpenTdbClient | Send HTTP requests to OpenTDB |
| QuestionParser | Parse and validate JSON question data |
| BankQuestion | Represent imported question data |
| QuestionBankService | Save questions and skip duplicates |
| FetchQuestionsTask | Download trivia questions using a background task |
| JsonImportTask | Import questions from local JSON files |
| ScoreJsonImporter | Import historical scores asynchronously |
| AnalyticsDao | Perform analytical database queries |
| M2GameScreen | Display the separate M2 multiplayer game interface |
| RoundRobinGame | Manage multiplayer turns and scoring |
| Home and Navigator | Handle navigation between application screens |

### Design Rationale

The application follows a layered architecture separating question models, game logic, database operations, API integration, background processing, and JavaFX interfaces.

The primary JavaFX application starts from App, initializes SQLite, and displays Home.

NewGameScreen creates and configures GameEngine, while GameScreen presents questions and handles player input.

GameEngine uses a Queue for question ordering and maintains an Undo history. ScoreDao handles database score persistence.

Database stores application records in data/trivia.db. It creates the data directory when necessary and enables SQLite foreign-key enforcement.

OpenTdbClient uses Java HttpClient to request trivia questions. QuestionParser converts the JSON response into BankQuestion records.

QuestionBankService stores questions in SQLite and skips duplicates.

FetchQuestionsTask and JsonImportTask perform question operations outside the JavaFX application thread.

The separate M2 package provides a local multiplayer demonstration through RoundRobinGame and M2GameScreen. Integration with the primary Home navigation remains pending.

JUnit 5 tests cover game logic, database access, recursion, question parsing, and edge cases.

---

## 4. Data Model & Database Design

### Finalized ER Diagram
*(Figure 3: Finalized ER diagram)*

```
+---------------------+                      +----------------------+
|     CATEGORIES      |                      |      QUESTIONS       |
+---------------------+                      +----------------------+
| PK id               |1                    M| PK id                |
|    name             |<---------------------+    text              |
| FK parent_id        |                      |    correct_answer    |
+----------+----------+                      |    incorrect_answers |
 1         | M  (parent_id -> id)            |    difficulty        |
 +---------+                                 |    question_type     |
           | 1                               | FK category_id       |
           |                                 +-----------+----------+
           | M                                           | 1
           v                                             | M
+------------------------------------------------------------------+
|                              SCORES                              |
+------------------------------------------------------------------+
| PK id                                                            |
| FK session_id                                                    |
| FK player_id                                                     |
| FK category_id                                                   |
| FK question_id                                                   |
|    is_correct (0 or 1)                                           |
|    points                                                        |
+------------------------------------------------------------------+
           ^                                            ^
           | M                                          | M
           | 1                                          | 1
+----------+----------+                      +----------+----------+
|       PLAYERS       |                      |    GAME_SESSIONS    |
+---------------------+                      +---------------------+
| PK id               |                      | PK id               |
|    name             |                      |    played_at        |
+---------------------+                      +---------------------+
```

### Table 5: Relationship and Cardinality
| Relationship | Cardinality | Foreign Key | Meaning |
| :--- | :--- | :--- | :--- |
| **categories $\rightarrow$ categories** | 1:M | `categories.parent_id` | A category has many subcategories (`NULL` = top level) |
| **categories $\rightarrow$ questions** | 1:M | `questions.category_id` | A category contains many questions; each question has exactly one category |
| **categories $\rightarrow$ scores** | 1:M | `scores.category_id` | Each answer is recorded under one category |
| **questions $\rightarrow$ scores** | 1:M | `scores.question_id` | A question can be answered many times |
| **players $\rightarrow$ scores** | 1:M | `scores.player_id` | A player has many score rows |
| **game_sessions $\rightarrow$ scores** | 1:M | `scores.session_id` | A session has many score rows |
| **players $\leftrightarrow$ game_sessions** | M:N | Resolved by `scores` | A player joins many sessions, and a session has many players |

---

### SQLite Schema
This is the schema `Database.initializeSchema()` creates (shortened; see `Database.java` for the full code).

```sql
PRAGMA foreign_keys = ON;   -- run on every connection

CREATE TABLE IF NOT EXISTS categories (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL,
    parent_id INTEGER REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS players (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS game_sessions (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS questions (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    text              TEXT NOT NULL,
    correct_answer    TEXT NOT NULL,
    incorrect_answers TEXT NOT NULL,        -- JSON array of strings
    difficulty        TEXT NOT NULL,
    question_type     TEXT NOT NULL DEFAULT 'MULTIPLE_CHOICE',
    category_id       INTEGER NOT NULL REFERENCES categories(id)
);

CREATE TABLE IF NOT EXISTS scores (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES game_sessions(id),
    player_id   INTEGER NOT NULL REFERENCES players(id),
    category_id INTEGER NOT NULL REFERENCES categories(id),
    question_id INTEGER NOT NULL REFERENCES questions(id),
    is_correct  INTEGER NOT NULL CHECK (is_correct IN (0, 1)),
    points      INTEGER NOT NULL
);
```

### Design Notes
- `incorrect_answers` stores the wrong options as a JSON array, for example `["Earth","Venus","Jupiter"]`. It is read back with Jackson, so answers that contain commas or quotes survive. Storing them in one column keeps the model simple. A separate `answer_option` table would be the fully normalized alternative.
- `question_type` (`MULTIPLE_CHOICE` or `TRUE_FALSE`) tells `QuestionMapper` which `Question` subclass to build. `initializeSchema()` adds the column to older database files that lack it.
- A `CHECK` constraint keeps `is_correct` at 0 or 1. The DAO classes also reject empty text, answers, difficulty or names, non-positive IDs, and negative points before anything is saved, and `ScoreJsonImporter` checks the type and range of every field in each JSON entry.
- Duplicate questions are prevented through `QuestionBankService.importQuestions()`. The service normalizes category names and question text, checks existing SQLite records, and skips duplicate questions during import. Duplicate prevention is implemented in application code rather than through a `UNIQUE` database constraint.
- Every SQL statement in the DAO classes uses `PreparedStatement`, and `Database` turns on `PRAGMA foreign_keys` for each connection, since SQLite does not enforce foreign keys by default.
- Deleting a player who already has scores is blocked by the foreign key, and the Players screen shows a friendly message instead of the raw error.

---


## 5. API Integration Design

### Public API

The application uses the Open Trivia Database (OpenTDB), a public trivia API that does not require an API key.

The API is an optional source for downloading additional questions. Normal gameplay uses locally stored questions, supporting offline operation.

### Actual API Classes

| Class | Responsibility |
| :--- | :--- |
| `OpenTdbClient` | Sends HTTP requests to OpenTDB |
| `QuestionParser` | Parses JSON responses and validates question data |
| `BankQuestion` | Represents an imported question |
| `FetchQuestionsTask` | Fetches and imports questions on a background JavaFX task |
| `JsonImportTask` | Imports trivia questions from a local JSON file |
| `QuestionBankService` | Saves questions to SQLite and skips duplicates |

### Endpoint

The current application requests:

`GET https://opentdb.com/api.php?amount=10&encode=url3986`

### Table 6: API Request Parameters

| Parameter | Current implementation | Purpose |
| :--- | :--- | :--- |
| `amount` | 1 to 50 | Number of questions requested |
| `encode` | `url3986` | Percent-encodes question and answer text |
| `category` | Not currently sent | No API category filter implemented |
| `difficulty` | Not currently sent | No API difficulty filter implemented |

The current OpenTdbClient uses a 12-second connection timeout and a 25-second request timeout.

### Multiple-Choice Answer Limitations

OpenTDB returns one correct answer and three incorrect answers for a standard multiple-choice question.

Therefore, each multiple-choice question normally has four answer options.

The number of answer choices cannot be configured through the OpenTDB API.

True/False questions contain two choices: True and False.

The application's MultipleChoiceQuestion class can display a variable number of choices, allowing appropriately formatted imported questions to use different answer counts.

The download amount is separate from the answer count.

### Response Processing

1. OpenTdbClient sends an HTTP GET request using Java HttpClient.

2. The response is passed to QuestionParser.

3. QuestionParser uses Jackson to parse the JSON response, verifies the response code, decodes question text, and validates each question.

4. Multiple-choice questions are mapped to MULTIPLE_CHOICE, while boolean questions are mapped to TRUE_FALSE.

5. Parsed questions are represented by BankQuestion records.

6. FetchQuestionsTask passes downloaded questions to QuestionBankService.

7. QuestionBankService finds or creates categories, checks for duplicate questions, and saves new questions through QuestionDao.

8. Questions are stored in SQLite and can be reused for offline gameplay.

### Table 7: API Error Handling

| Situation | Current handling |
| :--- | :--- |
| Invalid download amount | IllegalArgumentException |
| HTTP status other than 200 | IOException with HTTP status |
| Invalid OpenTDB response code | IOException from QuestionParser |
| Malformed or incomplete JSON | Parsing exception |
| Network failure or timeout | Exception reported by the background task |
| Duplicate question | Skipped by QuestionBankService |

The M2GameScreen displays import status and reports task failures while retaining access to the offline question bank.

### Background Processing

FetchQuestionsTask extends JavaFX Task and performs the network request and SQLite import outside the JavaFX application thread.

JsonImportTask also runs in the background to read and save local JSON question files.

Both operations are available in the separate M2GameScreen demonstration implementation.

The default main Home navigation still requires integration before these controls are available through the primary application.

---

## 6. Concurrency Design

The application performs potentially slow HTTP requests and JSON imports on background threads to keep the JavaFX interface responsive.

### Table 8: Background Operations

| Operation | Implementation | Background Processing | UI Update |
| :--- | :--- | :--- | :--- |
| Import historical scores | ScoreJsonImporter and ImportScreen | CompletableFuture using a single-thread executor | Platform.runLater |
| Download trivia questions | FetchQuestionsTask and M2GameScreen | JavaFX Task on a daemon worker thread | setOnSucceeded and setOnFailed |
| Import local trivia questions | JsonImportTask and M2GameScreen | JavaFX Task on a daemon worker thread | setOnSucceeded and setOnFailed |

### API Download Process

1. The user requests additional questions in M2GameScreen.
2. M2GameScreen starts FetchQuestionsTask.
3. FetchQuestionsTask calls OpenTdbClient.
4. OpenTdbClient sends an HTTP request to OpenTDB.
5. QuestionParser validates and parses the returned JSON.
6. QuestionBankService checks for duplicates and saves new questions to SQLite.
7. JavaFX Task handlers display the result or error.

Downloaded questions remain available for offline gameplay.

### JSON Question Import Process

1. The user selects a local JSON file.
2. M2GameScreen creates JsonImportTask.
3. QuestionParser reads and validates the questions.
4. QuestionBankService saves new questions to SQLite.
5. The JavaFX interface displays the import result.

### Historical Score Import

ScoreJsonImporter uses CompletableFuture with a single-thread executor.

ImportScreen receives completion results through whenComplete and Platform.runLater.

This allows large JSON score imports to run without blocking the JavaFX application thread.

### Thread Safety and UI Responsiveness

Network requests and database imports run outside the JavaFX application thread.

JavaFX controls are updated on the application thread through JavaFX Task handlers or Platform.runLater.

The corresponding import buttons are disabled while question import tasks are running and re-enabled after completion.

The API download and question JSON import functions currently belong to M2GameScreen, which is separate from the main Home navigation.

### Verification

The application should be tested to confirm that background imports do not freeze the interface.

Tests should also verify that errors are reported and previously stored questions remain accessible after a failed API request.

## 7. Data Mining & Visualization Design

The Trivia & Quiz Night Manager uses data mining and visualization to analyze players' performance based on their saved game results.

The application retrieves actual data from its SQLite database, performs SQL aggregation, and presents the results through JavaFX charts.

### 7.2 Data Mining and Aggregation

The `AnalyticsDao.java` class performs data analysis using SQL queries.

The following database tables are used:

* `scores` — stores players' answers, correctness, and earned points.
* `players` — stores player information.
* `categories` — identifies the category of each question.
* `game_sessions` — identifies individual game sessions.

The application uses SQL operations such as `COUNT()`, `SUM()`, `AVG()`, and `GROUP BY` to calculate performance statistics.

#### Category Accuracy

The application calculates the percentage of correct answers for each category.

Accuracy is calculated using:

Accuracy (%) = (Correct Answers / Total Answers) × 100

For example, if players answer 8 out of 10 Science questions correctly:

Accuracy = (8 / 10) × 100 = 80%

The result is calculated using the actual saved answers rather than hard-coded values.

#### Points per Game

The application also calculates the total points earned by an individual player during each game session.

The `getPointsPerSession()` method groups scores by game session and calculates their totals using `SUM(points)`.

These results allow users to compare a player's performance across multiple games.

### 7.3 Data Visualization

The application uses the JavaFX chart components implemented in `AnalyticsScreen.java`.

#### Bar Chart — Accuracy by Category

The bar chart displays the percentage of correct answers for each trivia category.

* X-axis: Category
* Y-axis: Accuracy (%)
* Data source: `AnalyticsDao.getAccuracyByCategory()`

A bar chart was selected because it makes it easy to compare accuracy percentages across different categories and identify which topics players find easiest or most difficult.

#### Line Chart — Points per Game

The line chart shows a selected player's total points across saved game sessions.

* X-axis: Game session (date played)
* Y-axis: Points earned
* Data source: `AnalyticsDao.getPointsPerSession()`

A line chart was selected because it allows users to observe changes in player performance across successive game sessions.

### 7.4 Dynamic Data Updates

The charts are generated using actual data stored in SQLite.

When a game is completed or new scores are successfully imported, the database contains additional score records.

The user can open the Analytics screen or click Refresh to recalculate the statistics and update the charts.

For example, if a category originally contains 8 correct answers out of 10 attempts, its accuracy is 80%.

If two additional incorrect answers are saved, its accuracy becomes approximately 66.67%.

This demonstrates that changes in the underlying database affect both the calculated results and the visualizations.

### 7.5 Implementation and Design Rationale

| Component         | Java Class               | Responsibility                                       |
| ----------------- | ------------------------ | ---------------------------------------------------- |
| Database          | `Database.java`          | Provides SQLite database connections                 |
| Data Mining       | `AnalyticsDao.java`      | Performs SQL aggregation and analysis                |
| Visualization     | `AnalyticsScreen.java`   | Displays bar and line charts using JavaFX            |
| Score Persistence | `ScoreDao.java`          | Saves player scores used in analytics                |
| JSON Import       | `ScoreJsonImporter.java` | Adds imported scores that can contribute to analysis |

The design separates the data-processing layer from the presentation layer.

`AnalyticsDao` handles database queries and calculations, while `AnalyticsScreen` handles chart rendering and user interaction.

This separation follows the Single Responsibility Principle and allows the visualization interface to be modified without rewriting the SQL analysis logic
