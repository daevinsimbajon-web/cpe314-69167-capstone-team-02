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
| **0.1** | 10/24/26 - 10/26/26 | Justine B. Cuizon, Clarence A. Elegino, Joshua G. Malaya, Gebrielle P. Mirasol, John S. Simbajon | Initial draft — Week 8 sections (Section 1 to 2). Removing Unnecessary Instruction. Generating the Table of Contents. |
| **0.2** | 10/28/26 | Group 2 | Week 9 sections added. |
| **0.3** | 11/05/26 | Group 2 | Week 10 sections added (Sections 5 and 6). Sections 1, 2, 3 and 4 revised to match the implemented code: real package and class names, real database schema, JSON score import instead of CSV, corrected team roles table. |
| **1.0** | *Pending* | Group 2 | Final compilation and polish — Week 11. |

---

## 1. Introduction & Project Overview

**Trivia & Quiz Game Night Manager** is a Java desktop application (JavaFX) that helps friends, classmates, and community groups organize and run trivia nights. The group can download fresh questions from the public Open Trivia Database or play the questions already saved on the computer. Players are chosen from a saved player list, pick a category and a number of questions, and answer them one at a time, with an "undo last answer" option for mistakes. Every player, game session, and score is saved in a local SQLite database, and past score history can be imported from a JSON file. The app then analyzes the saved results and shows a leaderboard, accuracy per category as a bar chart, and points earned in each game as a line chart.

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
- Duplicate questions are prevented in code, not by a `UNIQUE` constraint: `QuestionDownloader` compares the trimmed, lower-cased question text with what is already saved, and also within the same download.
- Every SQL statement in the DAO classes uses `PreparedStatement`, and `Database` turns on `PRAGMA foreign_keys` for each connection, since SQLite does not enforce foreign keys by default.
- Deleting a player who already has scores is blocked by the foreign key, and the Players screen shows a friendly message instead of the raw error.

---

## 5. API Integration Design

**Public API:** the [Open Trivia Database](https://opentdb.com/api_config.php) (no API key needed). The class `OpenTriviaClient` sends the request, and `QuestionDownloader` saves the result.

### Endpoint
```
GET https://opentdb.com/api.php?amount=10&category=17&encode=url3986
```

### Table 4: Request parameters
| Parameter | Value in our app | Why |
| :--- | :--- | :--- |
| `amount` | 1 to 50 (chosen in the Download screen) | The API allows at most 50 questions per call. |
| `category` | Open Trivia category ID, chosen from `ApiCategory.DEFAULTS` (for example 17 = Science & Nature, 23 = History) | Each API category is mapped to one of our local top-level categories (Science, History, Entertainment). |
| `encode` | `url3986` | Percent-encoding avoids HTML entities such as `&quot;` and `&#039;` in question text. The client decodes every text field with `URLDecoder`. |

### Shape of the JSON response
```json
{
  "response_code": 0,
  "results": [
    {
      "type": "multiple",
      "difficulty": "easy",
      "category": "Science%20%26%20Nature",
      "question": "Which%20planet%20is%20the%20Red%20Planet%3F",
      "correct_answer": "Mars",
      "incorrect_answers": ["Earth", "Venus", "Jupiter"]
    }
  ]
}
```

### How the response is parsed and used
1. `OpenTriviaClient.fetchQuestions(amount, categoryId)` builds the URI, sends the request through its `Transport` (a `java.net.http.HttpClient` with 5 s connect and 10 s request timeouts), and passes the body to `parse`.
2. `parse` uses Jackson to read the tree. It checks `response_code` first, then reads each entry of `results` into an immutable `ApiQuestion` record (type, difficulty, category, text, correct answer, wrong answers), decoding every text field. Entries with a missing field, a bad escape, or an unknown type are skipped instead of stopping the whole download.
3. `"multiple"` becomes `MULTIPLE_CHOICE` and `"boolean"` becomes `TRUE_FALSE`.
4. `QuestionDownloader.download(category, amount)` fetches first, so going offline changes nothing in the database. It then finds or creates the local category, skips questions already saved (compared by trimmed, lower-cased text, including twice in the same download), and saves the rest with `QuestionDao.addQuestion`.
5. The saved questions are used like any others: `NewGameScreen` loads them with `getQuestionsRecursively`, `QuestionMapper` turns them into `Question` objects, and the game is played and scored from them. The download therefore has a real effect on gameplay and later on the analytics, and is not just printed.

### Table 5: How API problems are handled
| Situation | Result shown to the user (`ApiException` message) |
| :--- | :--- |
| No internet or timeout (`IOException`) | "Could not reach the Open Trivia Database. Check your internet connection and try again." |
| HTTP 429 or `response_code` 5 | "Too many requests. The Open Trivia Database allows one download every 5 seconds. Wait a moment and try again." |
| `response_code` 1 | The category does not have enough questions; ask for fewer. |
| `response_code` 2 | Invalid parameter. |
| Other HTTP status or `response_code` | Message with the status or code. |
| Malformed JSON, missing `results` | Error that the answer could not be read. |
| Download cancelled (thread interrupted) | "cancelled"; the interrupt flag is restored. |

The Open Trivia Database allows one request per 5 seconds per IP address, so the Download screen shows the rate-limit message instead of retrying automatically.

*(Figure 4: Data flow of one download)*
```
Download button -> DownloadScreen --(Task, background thread)--> QuestionDownloader
   -> OpenTriviaClient --HTTPS--> opentdb.com
   -> parse JSON -> ApiQuestion list
   -> skip duplicates -> QuestionDao / CategoryDao -> SQLite
   -> (JavaFX thread) status text + results box updated
```

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
