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
| **Document Version / Date:** | V0.2 - 10/28/26 |

---

## Table of Contents
- [Revision History](#revision-history)
- [1. Introduction & Project Overview](#1-introduction--project-overview)
- [2. Preliminary ER Sketch & Team Roles](#2-preliminary-er-sketch--team-roles)
- [3. System Architecture & Object-Oriented Design](#3-system-architecture--object-oriented-design)
- [4. Data Model & Database Design](#4-data-model--database-design)

---

## Revision History
*Update this table each week as your team adds or revises sections of this document.*

| Version | Date | Authors | Changes |
| :--- | :--- | :--- | :--- |
| **0.1** | 10/24/26 - 10/26/26 | Justine B. Cuizon, Clarence A. Elegino, Joshua G. Malaya, Gebrielle P. Mirasol, John S. Simbajon | Initial draft — Week 8 sections (Section 1 to 2). Removing Unnecessary Instruction. Generating the Table of Contents. |
| **0.2** | 10/28/26 | Group 2 | Week 9 sections added. |
| **0.3** | *Pending* | Group 2 | Week 10 sections added. |
| **1.0** | *Pending* | Group 2 | Final compilation and polish — Week 11. |

---

## 1. Introduction & Project Overview

**Trivia & Quiz Game Night Manager** is a Java desktop application that helps friends, classmates, and community groups organize and run trivia nights. Players enter their names, choose a category, and answer questions fetched live from the Open Trivia Database, with an "undo last answer" option for mistakes. Every player, game session, and score is saved in a local SQLite database, and past score history can be imported from CSV files. The app then analyzes the saved results and shows average score per category, most-missed questions, and score trends over time as labeled bar and line charts.

- **Theme:** Custom idea (an entertainment and social-gaming tool for trivia nights).
- **Problem solved:** Quiz hosts usually keep score on paper or in scattered spreadsheets, so results are easy to lose and hard to compare from one night to the next.
- **Key value proposition:** One app that fetches questions, scores each player, stores the history, and turns it into clear charts, so groups can see who is improving and which categories are hardest.

---

## 2. Preliminary ER Sketch & Team Roles

### Preliminary ER Sketch
*(Figure 1: Entities, Key Attributes and Relationships)*

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
| **Gebrielle P. Mirasol** | Core OOP/Logic Developer |
| **Justine B. Cuizon** | Database Admin + Data Developer + API |
| **Clarence Philip A. Elegino** | UI/UX Designer + JavaFX Developer |
| **Joshua Malaya** | Analytics + QA |

---

## 3. System Architecture & Object-Oriented Design

### Class Hierarchy
*(Figure 2: Class hierarchy)*

```
+-------------------------------+  +-------------------------------+  +-----------------------------------+
|        ScienceQuestion        |  |        HistoryQuestion        |  |       EntertainmentQuestion       |
| (base points by difficulty)   |  | (flat points, no time bonus)  |  |  (speed bonus for fast answers)   |
+---------------+---------------+  +---------------+---------------+  +-----------------+-----------------+
                |                                  |                                    |
                +----------------------------------+------------------------------------+
                                                   |
                                                   v
                            +----------------------------------------------+
                            |            <<abstract>> Question             |
                            |----------------------------------------------|
                            | (text, correctAnswer, incorrectAnswers,      |
                            |  difficulty, category)                       |
                            |----------------------------------------------|
                            | + checkAnswer(String): boolean               |
                            | + <<abstract>> calculatePoints(              |
                            |     boolean correct, int secondsTaken): int  |
                            +----------------------------------------------+
```

### Supporting Classes by Package
- **`trivia.model`**: `Question` (abstract), `ScienceQuestion`, `HistoryQuestion`, `EntertainmentQuestion`, `Player`, `Category`
- **`trivia.db`**: `Database`, `PlayerDao`, `QuestionDao`, `CategoryDao`, `ScoreDao`
- **`trivia.api`**: `OpenTdbClient`, `QuestionParser`
- **`trivia.game`**: `GameEngine` (Queue + Stack), `CategoryTree` (recursion)
- **`trivia.analytics`**: `AnalyticsService`
- **`trivia.io`**: `CsvImportTask` (background thread)
- **`trivia.ui`**: `MainApp`, `SetupView`, `GameView`, `HistoryView`, `AnalyticsView`, `AnalyticsCharts`

### Table 2: How each class follows the Single Responsibility Principle
| Class | Its single responsibility |
| :--- | :--- |
| **Question and subclasses** | Represent one question and compute its own points. Each subclass changes only its own scoring rule. |
| **Player, Category** | Hold data for one entity. No database or GUI code. |
| **Database** | Open the SQLite connection and run the schema. |
| **PlayerDao, QuestionDao, CategoryDao, ScoreDao** | Run the SQL (`PreparedStatement`) for one table each. |
| **OpenTdbClient** | Send the HTTP request and return the raw JSON. |
| **QuestionParser** | Turn JSON text into `Question` objects. |
| **GameEngine** | Run one game: Queue for question order, Stack for undo. No GUI or database code. |
| **CategoryTree** | Walk the category $\rightarrow$ subcategory tree recursively. |
| **AnalyticsService** | Compute averages, most-missed questions, and trends. It does not draw charts. |
| **CsvImportTask** | Read a CSV file on a background thread and report progress. |
| **AnalyticsCharts and *View classes** | Display data only. They call the other classes and contain no business logic. |

---

## 4. Data Model & Database Design

### Finalized ER Diagram
*(Figure 3: Finalized ER diagram)*

```
+---------------------+                      +---------------------+
|      CATEGORY       |                      |      QUESTION       |
+---------------------+                      +---------------------+
| PK id               |1                    1| PK id               |
|    name             |<--------------------+|    text             |
| FK parent_id        |                      |    correct_answer   |
+----------+----------+                      |    incorrect_answers|
 1         | M                               |    difficulty       |
 +---------+                                 | FK category_id      |
           | 1                               +----------+----------|
           |                                            | 1
           | M                                          | M
           v                                            v
+------------------------------------------------------------------+
|                              SCORE                               |
+------------------------------------------------------------------+
| PK id                                                            |
| FK category_id                                                   |
| FK question_id (nullable)                                        |
| FK session_id                                                    |
| FK player_id                                                     |
|    is_correct                                                    |
|    points                                                        |
+------------------------------------------------------------------+
           ^                                            ^
           | M                                          | M
           | 1                                          | 1
+----------+----------+                      +----------+----------+
|       PLAYER        |                      |    GAME_SESSION     |
+---------------------+                      +---------------------+
| PK id               |                      | PK id               |
|    name             |                      |    played_at        |
+---------------------+                      +---------------------+
```

### Table 3: Relationship and Cardinality
| Relationship | Cardinality | Foreign Key | Meaning |
| :--- | :--- | :--- | :--- |
| **category $\rightarrow$ category** | 1:M | `category.parent_id` | A category has many subcategories (optional parent; `NULL` = top level) |
| **category $\rightarrow$ question** | 1:M | `question.category_id` | A category contains many questions; each question has exactly one category |
| **category $\rightarrow$ score** | 1:M | `score.category_id` | Each answer is recorded under one category |
| **player $\rightarrow$ score** | 1:M | `score.player_id` | A player has many score rows |
| **game_session $\rightarrow$ score** | 1:M | `score.session_id` | A session has many score rows |
| **question $\rightarrow$ score** | 1:M (optional) | `score.question_id` | A question can be answered many times. `NULL` for rows imported from CSV |
| **player $\leftrightarrow$ game_session** | M:N | Resolved by `score` | A player joins many sessions, and a session has many players |

---

### SQLite Schema

```sql
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS category (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL UNIQUE,
    parent_id INTEGER REFERENCES category(id)
);

CREATE TABLE IF NOT EXISTS player (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS question (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    text              TEXT NOT NULL UNIQUE,
    correct_answer    TEXT NOT NULL,
    incorrect_answers TEXT NOT NULL,
    difficulty        TEXT NOT NULL CHECK (difficulty IN ('easy','medium','hard')),
    category_id       INTEGER NOT NULL REFERENCES category(id)
);

CREATE TABLE IF NOT EXISTS game_session (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    played_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS score (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES game_session(id),
    player_id   INTEGER NOT NULL REFERENCES player(id),
    category_id INTEGER NOT NULL REFERENCES category(id),
    question_id INTEGER REFERENCES question(id),
    is_correct  INTEGER NOT NULL CHECK (is_correct IN (0,1)),
    points      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_score_player   ON score(player_id);
CREATE INDEX IF NOT EXISTS idx_score_category ON score(category_id);
CREATE INDEX IF NOT EXISTS idx_score_question ON score(question_id);
```

### Design Notes
- `incorrect_answers` stores the wrong options joined with `|` to keep the model simple. This is a deliberate trade-off, and a separate `answer_option` table would be the fully normalized alternative.
- `UNIQUE` on `question.text` prevents duplicate questions when the same API batch is fetched twice.
- `CHECK` constraints keep `difficulty` and `is_correct` valid even if bad data arrives from a CSV import.
- Every SQL statement in the DAO classes will use `PreparedStatement`, and the `Database` class turns on `PRAGMA foreign_keys` for each connection, since SQLite does not enforce foreign keys by default.