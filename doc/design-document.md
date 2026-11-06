# CPE 314 — Software Design
**University of Cebu**  
**Computer Engineering Department**

## Trivia & Quiz Night Manager
### Project Design Document

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

### Table 1: Team members and primary roles
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

- **`questions`**: `Question` (abstract), `MultipleChoiceQuestion`, `TrueFalseQuestion`, `QuestionMapper` (turns a saved database row into the right `Question` subclass)
- **`category`**: `Category` (in-memory tree with recursive `findCategory` and `getAllQuestions`)
- **`db`**: `Database`, `PlayerDao`, `QuestionDao`, `CategoryDao` (including the recursive `getQuestionsRecursively`), `ScoreDao`, `AnalyticsDao`, `SampleQuestionSeeder`, `ScoreJsonImporter`, `DatabaseDemo`
- **`api`**: `OpenTriviaClient`, `ApiQuestion`, `ApiCategory`, `ApiException`, `QuestionDownloader`
- **`gameplay`**: `GameEngine` (Queue + Stack), `NewGameScreen`, `GameScreen`
- **`analytics`**: `AnalyticsScreen`
- **`results`**: `Results`
- **`importer`**: `ImportScreen`
- **`download`**: `DownloadScreen`
- **`players`**: `Players`
- **`home`**: `Home`, `Navigator`, `Ui`
- **root**: `App` (JavaFX entry point, loads `style.css`), `Main`

### Table 2: How each class follows the Single Responsibility Principle
| Class | Its single responsibility |
| :--- | :--- |
| **Question and subclasses** | Represent one question and decide its own points, choices, and answer check. No database or GUI code. |
| **QuestionMapper** | Convert one saved question row into a `Question` object (reads the wrong answers with Jackson). |
| **Category** | Hold one category with its questions and subcategories, and search the tree recursively. |
| **Database** | Open the SQLite connection (foreign keys on) and create the tables. |
| **PlayerDao, CategoryDao, QuestionDao, ScoreDao** | Run the SQL (`PreparedStatement`) for one table each. `CategoryDao` also walks the category tree recursively. |
| **AnalyticsDao** | Run the read-only aggregation queries (accuracy, rankings, per-game points, best category). It does not draw charts. |
| **ScoreJsonImporter** | Read a JSON file of past scores, validate each entry, and save the valid ones. It never touches the GUI. |
| **OpenTriviaClient** | Build the HTTP request, call the Open Trivia Database, and turn the JSON answer into `ApiQuestion` objects. It never touches the database. |
| **QuestionDownloader** | Take downloaded questions, skip duplicates, and save the new ones through the DAOs. |
| **GameEngine** | Run one game: Queue for question order, Stack for undo, saving scores through `ScoreDao`. No GUI code. |
| **Home, Navigator, Ui** | Sidebar navigation, the interface the screens use to move between each other, and small helpers that build labels and buttons. |
| **Players, NewGameScreen, GameScreen, Results, AnalyticsScreen, ImportScreen, DownloadScreen** | One screen each. They display data and react to clicks and call the classes above, but contain no SQL and no scoring rules. |

### Design Rationale
The project is layered so each layer only talks to the one below it: screens call `GameEngine`, the importer, the downloader, and the DAOs; those call `Database`; the model classes (`Question`, `Category`) know nothing about either. This kept the five members working in parallel (each owned one layer) and made most of the code testable without a window. Two choices make testing easier. `OpenTriviaClient` receives its HTTP call through a small `Transport` interface, so tests supply a fake answer and need no internet. `Database` reads its file path from the `trivia.db` system property, so tests use `target/test.db` and never touch the real game data. Question types use inheritance and polymorphism because the screens must treat every type the same way, while the rules (points, choices, answer check) differ per type.

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

### Table 3: Relationship and Cardinality
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

### Table 4: API Request Parameters

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

### Table 5: API Error Handling

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

### Table 6: Long-running operations and how each runs
| Operation | Class | Threading approach | UI update |
| :--- | :--- | :--- | :--- |
| **Import a score history JSON file** (thousands of entries, many database writes) | `ScoreJsonImporter.importAsync` called from `ImportScreen` | One daemon worker thread from `Executors.newSingleThreadExecutor`, started with `CompletableFuture.supplyAsync(..., executor)` | `whenComplete` hands the result to `Platform.runLater`, which updates the status, closes the progress indicator, and re-enables the buttons. |
| **Download questions from the internet** (network wait plus database writes) | `QuestionDownloader` called from `DownloadScreen` | A `javafx.concurrent.Task` run on its own daemon thread | `Task.setOnSucceeded` and `setOnFailed` run on the JavaFX thread and update the status and the results box. |

### Approach
JavaFX allows controls to be touched only on the JavaFX Application Thread. So both operations follow the same pattern:
1. The click handler (on the JavaFX thread) disables the buttons, shows a `ProgressIndicator`, and starts the work on a background thread. It returns immediately.
2. The background thread does all slow work (file reading, HTTP, SQLite) and touches no control.
3. When the work ends, the result (or the error) goes back to the JavaFX thread (`Platform.runLater` or the `Task` handlers), which updates the screen.

The importer uses a single worker thread, so two imports run one after the other and never write to the database at the same time. Worker threads are daemon threads, so a running import never keeps the application from closing, and `Home.shutdown()` closes the importer when the window closes.

### How the UI stays responsive
While an import or download runs, the JavaFX thread is free, so the window keeps redrawing and the rest of the sidebar stays clickable. Only the Import and Download buttons are disabled, which prevents starting the same operation twice. To demonstrate it, import `sample_scores_2000.json` (2,000 entries) and click another sidebar item while the progress indicator is spinning.

*(Figure 5: Import thread flow)*
```
JavaFX thread                        score-json-importer thread
-------------                        --------------------------
Import clicked
  disable buttons, show progress
  importAsync(file) ----------------> read + validate entries
  (returns at once; UI is free)       save each valid score (SQLite)
                                      build ImportResult
  Platform.runLater(update UI) <----- result ready
  show counts, hide progress
```
