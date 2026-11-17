# Known Issues and Requirement-to-Code Map

Trivia & Quiz Night Manager · CPE 314 · Section 3A · Team 02

Status as of 13 November 2026, checked against the `project/milestone-2` branch.
Line numbers refer to that branch and will move as the code changes.

This file lists problems that are still in the code, then shows where each
course requirement is implemented.

## 1. Known issues

Each issue gives where it is, what is wrong, and the planned fix.

### Structure

**1. No controller layer.**
- *Where:* `home/Home.java`, `players/Players.java`, `results/Results.java`,
  `analytics/AnalyticsScreen.java`, `gameplay/NewGameScreen.java`,
  `m2/M2GameScreen.java`.
- *What:* The screens create their own DAOs and call `Database` or `java.sql`
  directly (Home creates 6 DAOs, Players 2, AnalyticsScreen 2, NewGameScreen 2,
  Results 1, M2GameScreen 1). Business rules and SQL live in the views.
- *Planned fix:* Add one controller per area (questions, players, game,
  results and analytics). The screens only show data and call the controller.

**2. Two game screens, and one of them is dead code.**
- *Where:* `gameplay/NewGameScreen.java` (532 lines),
  `m2/M2GameScreen.java` (886 lines), `gameplay/GameScreen.java` (262 lines).
- *What:* `NewGameScreen` and `M2GameScreen` implement the same game flow.
  `GameScreen` is never created anywhere.
- *Planned fix:* Keep one game screen, move the missing features into it, and
  delete the others.

**3. Several entry points.**
- *Where:* `App`, `Main` (an unused console game that uses `Scanner`),
  `m2/M2DemoApp`, `m2/M2Launcher`, plus `db/DatabaseDemo` and
  `db/SampleQuestionSeeder`, which also have a `main`.
- *What:* It is not clear which class starts the real application.
- *Planned fix:* Keep `App` as the only entry point, and delete or move the
  demo classes.

**4. A milestone name is used for a package and a class.**
- *Where:* package `m2`, class `M2GameScreen`.
- *What:* The name describes when the code was written, not what it does.
- *Planned fix:* Rename by purpose, for example `game` and `GameScreen`, and
  update the design document.

**5. `RoundRobinGame` is not pure game logic.**
- *Where:* `m2/RoundRobinGame.java` imports `QuestionDao`, `SessionPlayerDao`
  and `TimedAnswerDao`.
- *What:* Game rules and database access are in the same class.
- *Planned fix:* Move persistence into a service or DAO that the game calls.

### Threading

**6. Dashboard counts are read on the JavaFX thread.**
- *Where:* `home/Home.java` lines 137 to 145 (when the screen is built) and
  229 to 231 (when it refreshes).
- *What:* Four `count(...)` database queries run on the UI thread, so the
  window can freeze on a slow database.
- *Planned fix:* Run the counts in one background `Task` and update the labels
  when it finishes.

**7. The data layer creates and owns a thread pool.**
- *Where:* `db/ScoreJsonImporter.java` lines 75 to 78, `shutdown()` at line 578.
- *What:* `ImportScreen` never closes the importer, and `App.stop()` only calls
  `home.shutdown()`. Scheduling is mixed into the data layer.
- *Planned fix:* Make the importer synchronous and run it from a `Task`.

**8. Worker threads are started in several places.**
- *Where:* `home/Home.java:243`, `m2/M2GameScreen.java:496`,
  `importer/ImportScreen.java:190`.
- *What:* Each click creates a new thread, and there is no single executor.
- *Planned fix:* Use one owned executor that is shut down when the application
  closes.

**9. Other screens may also read the database on the UI thread.**
- *Where:* `Players`, `Results`, `AnalyticsScreen`, `NewGameScreen`.
- *What:* Not yet checked one by one.
- *Planned fix:* Review each screen and move slow reads into tasks.

### Data and schema

**10. `scores` stores both `category_id` and `question_id`.**
- *Where:* `db/Database.java` lines 91 to 97, `db/ScoreDao.java` (lines 78,
  116, 150, 167, 339), `db/ScoreJsonImporter.java:393`,
  `db/ScoreJsonExporter.java`, `db/AnalyticsDao.java` lines 74 and 240.
- *What:* A score's category can be found from its question, so the two
  columns can disagree. The design document describes the schema without
  `scores.category_id`, so the document and the code do not match.
- *Planned fix:* Remove the column, and join through `questions` in the
  analytics and export queries.

**11. The schema is created in four places.**
- *Where:* `Database.initializeSchema()` creates five tables.
  `SessionPlayerDao`, `TimedAnswerDao` and `QuestionBankService` each create
  one more.
- *What:* The full schema is not in one place.
- *Planned fix:* Create every table from `Database`.

**12. `Database` is a static helper.**
- *Where:* `db/Database.java`, used by every DAO.
- *What:* Nothing can be replaced with a fake in tests.
- *Planned fix:* Pass a connection or data source to each DAO.

**13. Few `UNIQUE` constraints.**
- *Where:* schema.
- *What:* Duplicate questions are prevented in application code only
  (`QuestionBankService`). The database enforces uniqueness only for the two
  sync IDs and for `(session_id, turn_order)`.
- *Planned fix:* Add constraints where the data allows.

### API and parser

**14. `QuestionParser` does too much.**
- *Where:* `api/QuestionParser.java` (423 lines).
- *What:* It parses and maps in one class, and it accepts alternative key names
  (`text`, `correctAnswer`, `question_type`, `questionType`). This is needed to
  read the local question-bank file, but it makes the class hard to follow.
  The client also has no result type such as OK, EMPTY or FAILED.
- *Planned fix:* Split OpenTDB parsing from question-bank parsing, and return a
  result type instead of throwing for an empty response.

**15. An exception is ignored.**
- *Where:* `api/QuestionParser.java:323`, `catch (Exception ignored)`.
- *What:* Errors are swallowed without a message.
- *Planned fix:* Catch the specific exception and report it.

**16. No saved API response for tests.**
- *Where:* `src/test/resources` does not exist.
- *What:* The parser is tested without a real OpenTDB response.
- *Planned fix:* Save one real response and test against it.

### Error handling

**17. Some errors only update a label, and nothing is logged.**
- *Where:* `m2/M2GameScreen.java` lines 251, 383, 545, 754, 785, 859.
- *What:* The user cannot tell whether a score was saved. There is no logger
  anywhere in `src/main`.
- *Planned fix:* Log the technical detail and show a short message in an alert.

**18. Broad `catch (Exception)`.**
- *Where:* `App.java:41`, `api/QuestionParser.java:323`,
  `m2/M2GameScreen.java:545` and `:754`, `gameplay/NewGameScreen.java:334`,
  `:349`, `:480`, `:500`.
- *What:* Eight places catch every exception.
- *Planned fix:* Catch the specific exceptions each call can throw.

### Tests

**19. Missing tests.**
- *Where:* `src/test`.
- *What:* There are no tests for `OpenTdbClient` (timeouts and bad response
  codes), `FetchQuestionsTask`, `JsonImportTask`, `RoundRobinGame`, or the UI
  logic. Nothing calls `CategoryDao.getQuestionsRecursively` directly; the
  recursive category search is only exercised by
  `GameEngineTest.testRecursiveCategory`.
- *Planned fix:* Add API client tests with a fake response, and task tests.

**20. A test file is in the wrong folder.**
- *Where:* `src/test/java/com/AnalyticsDaoTest.java` declares
  `package com.triviagame.db;`.
- *Planned fix:* Move it to `src/test/java/com/triviagame/db/`.

### Code standards

**21. Style problems.**
- *Where:* `GameEngine.java:18` has a stray second semicolon.
  `CategoryDao.java` (31 lines) and `QuestionDao.java` (1 line) use tab
  characters.
- *What (instructor's scan):* 19 blocks without braces, 10 lines over 100
  characters, 6 methods over 50 lines, 29 files without Javadoc, and 73 magic
  numbers. `M2GameScreen` is 886 lines.
- *Planned fix:* Fix these with the IDE formatter and inspections.

### Features and documentation

**22. Undo is missing from the main New Game screen.**
- *Where:* `gameplay/NewGameScreen.java` has no undo. `m2/M2GameScreen.java`
  has it, and `GameEngine.undo()` (line 128) implements it.
- *Planned fix:* Add an Undo button when the game screens are merged.

**23. Question JSON import is only reachable from `M2GameScreen`.**
- *Where:* `JsonImportTask` is created in `m2/M2GameScreen.java:440`.
- *Planned fix:* Add it to the Home screen.

**24. The README is out of date.**
- *Where:* `README.md` lines 78 and 80.
- *What:* It says the API client sends no difficulty filter (it does send
  difficulty and type), and that the main application and the M2 demo are not
  integrated.
- *Planned fix:* Correct both statements.

**25. Old score files can create duplicates.**
- *Where:* `db/ScoreJsonImporter.java`.
- *What:* JSON exported before the sync IDs existed has no IDs, so importing it
  twice can duplicate scores. `ImportScreen` warns about this (line 71).
- *Planned fix:* None planned. This is a known limitation.

**26. No user accounts.**
- *What:* The application has one host who types player names. Accounts with
  host and player roles are listed as planned in the design document.
- *Planned fix:* Out of scope for this milestone.

## 2. Requirement-to-code map

| Requirement | Where it is implemented | Tests |
| :--- | :--- | :--- |
| Public API with timeouts and response check | `api/OpenTdbClient.java` (12 s connect and 25 s request timeouts), `api/QuestionParser.java` (response code check), `tasks/FetchQuestionsTask.java` | `QuestionParserTest` |
| Threading, UI stays responsive | `FetchQuestionsTask` (started in `Home.java:217` and `M2GameScreen.java:406`), `JsonImportTask` (`M2GameScreen.java:440`), `ScoreJsonImporter.importAsync` (`ImportScreen.java:131`), score export task (`ImportScreen.java:190`) | `ScoreJsonImporterTest` (see issue 19) |
| Queue and stack | `gameplay/GameEngine.java`: `questions` (a `Deque` used as a queue) and `history` (a `Deque` used as an undo stack), `undo()` | `GameEngineTest`, `GameEngineDatabaseTest` |
| Recursion with a visited set | `db/CategoryDao.getQuestionsRecursively` (line 231, `visited` set at line 247) | `GameEngineTest.testRecursiveCategory` (see issue 19) |
| Data mining (aggregation) | `db/AnalyticsDao.java`: `getAccuracyByCategory`, `getPointsPerSession`, `getTopPlayers` and others | `AnalyticsDaoTest`, `AnalyticsInsightsTest` |
| Charts | `analytics/AnalyticsScreen.java`: `BarChart` (accuracy by category) and `LineChart` (points per game) | manual check |
| SQLite persistence | `db/Database.java` and the DAOs (`PlayerDao`, `QuestionDao`, `CategoryDao`, `ScoreDao`, `SessionPlayerDao`, `TimedAnswerDao`) | `DatabaseTest`, `PlayerDaoTest`, `QuestionDaoTest`, `CategoryDaoTest`, `ScoreDaoTest` |
| Local question bank and JSON question import | `db/QuestionBankService.java`, `tasks/JsonImportTask.java`, `src/main/resources/example_question_bank.json` | `QuestionImportTest`, `QuestionParserTest` |
| Score export and import without duplicates | `db/ScoreJsonExporter.java`, `db/ScoreJsonImporter.java` | `ScoreJsonImporterTest` |
| Question types | `questions/Question.java`, `MultipleChoiceQuestion`, `TrueFalseQuestion`, `QuestionMapper` | `MultipleChoiceQuestionTest`, `TrueFalseQuestionTest` |
| GUI | `home/Home.java`, `players/Players.java`, `gameplay/NewGameScreen.java`, `m2/M2GameScreen.java`, `results/Results.java`, `importer/ImportScreen.java`, `analytics/AnalyticsScreen.java` | manual check |
| JUnit tests | `src/test` (16 test files; the instructor's run passed 102 tests) | `mvn test` |
