# Hootly Requirements and Known Issues

This document maps the nine technical requirements and the 34 corrections in the Week 11 review to the current code. A completed implementation may still need to be demonstrated. See [Testing](TESTING.md) for results and the manual checks.

In the tables, desktop Java paths are relative to `src/main/java/com/triviagame/` or `src/test/java/com/triviagame/`. Online Java paths are relative to `online/backend/src/main/java/com/triviagame/online/` or its matching test package. Other paths are relative to the repository root.

## Nine Mandatory Technical Requirements

| No. | Requirement | Implementation | Status | Reference |
|---:|---|---|---|---|
| 1 | Object-oriented design and polymorphism | The desktop engine uses question subclasses and category-specific scoring. The online backend shares the question formats and uses interchangeable email transports. | Completed | Desktop `questions/Question.java`, `questions/CategoryQuestion.java`, `questions/CategoryQuestionTest.java`. Online `mail/EmailTransport.java`. |
| 2 | Data structures and recursion | The desktop engine uses a question queue and an answer stack. Leaderboard maps use player IDs. The online practice engine and personal category tree use these structures in private quizzes. | Completed | Desktop `gameplay/GameEngine.java`, `service/LeaderboardService.java`, `db/CategoryDao.java`, `gameplay/GameEngineTest.java`. Online `practice/PracticeEngine.java`, `bank/CategoryTree.java`, `CategoryTreeTest.java`. |
| 3 | JUnit 5 tests for normal and edge cases | Desktop and online tests cover scoring, timing, persistence, authorization, and invalid input. | Completed | Root and backend `pom.xml`, [test results](TESTING.md#recorded-results). |
| 4 | SQLite, JDBC, and prepared statements | The desktop DAOs use SQLite with parameterized statements. The web application uses PostgreSQL. | Completed | Desktop `db/Database.java`, `db/ScoreDao.java`, `db/ScoreSchemaMigrationTest.java`. [ERDs](ERD.md). |
| 5 | Public REST API and JSON | The Java client reads official OpenTDB results. Both applications use the data, and the online service saves selected questions as room snapshots. | Completed | `api/OpenTdbClient.java`, `api/OpenTdbResponseParser.java`, `api/OpenTdbContractTest.java`, online `bank/OpenTriviaService.java`, `OpenTriviaTest.java`. |
| 6 | Event-driven JavaFX GUI | The retained desktop interface has navigation, timed play, score import, and analytics. Online uses React. | Completed | Desktop `App.java`, `home/Home.java`, `gameplay/NewGameScreen.java`, `analytics/AnalyticsScreen.java`. |
| 7 | Background concurrency | JavaFX delegates slow work to tasks. The online backend schedules deadlines and queues statistics imports and email. | Completed | Desktop `App.java`, `tasks/FetchQuestionsTask.java`, `importer/ImportScreen.java`. Online `rooms/GameScheduler.java`, `analytics/StatisticsImportJobs.java`, `mail/EmailDelivery.java`. |
| 8 | Data mining from stored records | Desktop queries aggregate saved scores by category, player, and session. Online analytics use saved answers and completed games. | Completed | Desktop `db/AnalyticsDao.java`, `db/AnalyticsInsightsTest.java`. Online `analytics/AnalyticsRepository.java`, `AnalyticsExportTest.java`. |
| 9 | Labeled charts | The desktop and online dashboards display category and score charts with labels. | Completed | Desktop `analytics/AnalyticsScreen.java`, online `online/frontend/src/analytics/Charts.tsx` and `online/frontend/src/test/analytics-overview.test.tsx`. |

## Professor Corrections

| No. | Requirement | Implementation | Status | Reference |
|---:|---|---|---|---|
| 1 | List known issues and map the requirements to code | This file records the implementation, evidence, and open work. | Completed | This document |
| 2 | Separate controllers, screens, and database access | Screens delegate work to services and the game controller. Dedicated controllers and full game-session separation remain incomplete. | Partially Completed | Desktop `controller/GameController.java`, `gameplay/GameSession.java` |
| 3 | Organize model, DTO, service, DAO, controller, and UI packages | The question service and API DTOs have separate packages. Desktop DAOs remain in `db`, and the proposed layout is only partly adopted. | Partially Completed | Desktop `service/QuestionBankService.java`, `api/dto/`, `db/`, `controller/` |
| 4 | Consolidate entry points and duplicate game screens | The root Maven configuration starts a single player-facing desktop entry point and one desktop game screen. The seeder is retained as a utility. | Completed | Root `pom.xml`, desktop `App.java`, `home/Navigator.java`, `gameplay/NewGameScreen.java` |
| 5 | Remove milestone-specific class and package names | The current desktop game uses the `gameplay` package and `NewGameScreen`. | Completed | Desktop `gameplay/NewGameScreen.java` |
| 6 | Run database reads outside the JavaFX thread | Dashboard, player, result, analytics, and game setup screens schedule reads as background tasks. Interactive load checks remain. | Partially Completed | Desktop `home/Home.java`, `players/Players.java`, `results/Results.java`, `analytics/AnalyticsScreen.java`, `gameplay/NewGameScreen.java` |
| 7 | Own the executor and update controls on the JavaFX thread | The application owns and shuts down the worker pool. Task handlers update the controls. | Completed | Desktop `App.java`, `importer/ImportScreen.java` |
| 8 | Keep the score importer synchronous | `ImportScreen` starts a task that calls the synchronous importer. | Completed | Desktop `db/ScoreJsonImporter.java`, `importer/ImportScreen.java` |
| 9 | Test API, background work, and controller behavior | Tests cover provider responses and failures, task results, and controller behavior. | Completed | Desktop `api/OpenTdbClientTest.java`, `tasks/FetchQuestionsTaskTest.java`, `controller/GameControllerTest.java` |
| 10 | Cover edge cases consistently | Both suites contain boundary and failure cases. Edge-case coverage for every test class has yet to be reviewed individually. | Partially Completed | Desktop `db/DatabaseTest.java`, `gameplay/GameSessionTimingTest.java`. Online `IndependentGameplayTest.java` |
| 11 | Draw the ERD from the real schema | The PostgreSQL catalog and a newly initialized desktop SQLite schema supply the table, column, and key definitions. | Completed | [ERDs](ERD.md) |
| 12 | Correct the question-to-score cardinality | A desktop score has exactly one question through the required `question_id` FK. | Completed | Desktop `db/Database.java`, [SQLite ERD](ERD.md#sqlite-for-the-javafx-desktop-application) |
| 13 | Include the final ERD and remove placeholders | Section 4 embeds the desktop ERD and links the full PostgreSQL diagrams. | Completed | [Design document, Section 4](design-document.md#4-data-model-and-database-design) |
| 14 | Identify the join table and correct unique keys | `session_players` joins desktop sessions and players. The diagrams show actual UKs and group composite keys. | Completed | Desktop `db/SessionPlayerDao.java`, [ERDs](ERD.md) |
| 15 | Use the actual schema or cite the defining files | Section 4 links the desktop schema classes and both kinds of Flyway migration files. | Completed | [Design document, Section 4](design-document.md#4-data-model-and-database-design) |
| 16 | Derive the score’s category from its question | The SQLite migration removes the redundant column and retains scores. A conflicting historical category tied to a timed answer can block migration for review. | Completed | Desktop `db/Database.java`, `db/ScoreDao.java`, `db/ScoreSchemaMigrationTest.java` |
| 17 | Use revision numbers and dates | The current design document uses a numbered revision. The original proposal retains its historical heading. | Completed | [Design document](design-document.md), [proposal](proposal.md) |
| 18 | Describe each OpenTDB parameter correctly | The documentation lists amount, encoding, category, difficulty, and type and explains when filters are omitted. | Completed | Desktop `api/OpenTdbClient.java`, [Design document, Section 5](design-document.md#5-api-integration-design) |
| 19 | Document the places that start background work | Section 6 identifies the desktop tasks, room scheduler and WebSockets, and online import and email workers. | Completed | [Design document, Section 6](design-document.md#6-concurrency-design), desktop `App.java`, `home/Home.java`, `importer/ImportScreen.java` |
| 20 | Complete concurrency verification | Automated tests cover background work and game timing. Live JavaFX responsiveness and play across devices remain to be checked. | Partially Completed | Desktop `tasks/FetchQuestionsTaskTest.java`, `gameplay/GameSessionTimingTest.java`, [manual checks](TESTING.md#manual-verification) |
| 21 | Log technical errors and give clear user messages | Game setup and provider errors use short messages. Some broad catches and other error paths still need review. | Partially Completed | Desktop `gameplay/NewGameScreen.java`, `api/QuestionParser.java`. Online `bank/OpenTriviaService.java` |
| 22 | Remove the reported semicolon and tab problems | The deque declarations use one semicolon, and the two reported DAO files use spaces. | Completed | Desktop `gameplay/GameEngine.java`, `db/CategoryDao.java`, `db/QuestionDao.java` |
| 23 | Apply Java coding standards | Long methods, compact formatting, broad catches, and gaps in Javadoc remain in parts of the project. | Partially Completed | Desktop `gameplay/NewGameScreen.java`, online `rooms/IndependentGame.java` |
| 24 | Use API DTOs, a mapper, result types, and a saved fixture | The OpenTDB path parses a typed response, then maps and decodes each question. The contract test reads a saved response. | Completed | Desktop `api/dto/`, `api/OpenTdbQuestionMapper.java`, `api/OpenTdbResult.java`, `api/OpenTdbContractTest.java`, `src/test/resources/opentdb/live-response-2026-11-17.json` |
| 25 | Describe a user-based design and authorization | Online users have accounts and per-room roles. The application uses BCrypt and checks ownership and permissions. The two-device demonstration remains. | Partially Completed | Online `auth/AuthController.java`, `auth/SecurityConfiguration.java`, `rooms/RoomService.java`, `AuthenticationTest.java` |
| 26 | Answer the four design questions | The answers are recorded below. Team members must explain them during the defense. | Partially Completed | [Design questions](#design-questions-from-the-professor) |
| 27 | Explain individual contributions | The design document cites a verified commit for each member. The oral explanations are pending. | Partially Completed | [Design document, Section 9](design-document.md#9-individual-contributions) |
| 28 | Explain the host and player relationship | The design describes the online host and player roles and the separate local desktop workflow. | Completed | [Design document, Section 1](design-document.md#1-introduction-and-project-overview), online `rooms/RoomService.java` |
| 29 | Explain outages, insufficient results, and encoded text | The design describes cached categories, provider failures, the result count requirement, and the decoding step. | Completed | [Design document, Section 5](design-document.md#5-api-integration-design), desktop `api/OpenTdbQuestionMapper.java`, online `OpenTriviaTest.java` |
| 30 | Explain category uniqueness and duplicate player names | SQLite prevents duplicate sibling category names. PostgreSQL personal category names are unique per owner. Duplicate player display names remain distinct by ID. | Completed | [ERDs](ERD.md), desktop `db/Database.java`, `db/CategoryDaoTest.java`, `db/ScoreIdentityRoundTripTest.java` |
| 31 | Record question creation timestamps and external IDs | Both question schemas include creation timestamps and external IDs. SQLite allows a null external ID for local or older questions. | Completed | [ERDs](ERD.md), desktop `db/Database.java`, `db/QuestionImportTest.java`, online PostgreSQL V1 migration |
| 32 | Keep timing in the game engine and test with a fake clock | The desktop turn timer and online game service enforce deadlines. Tests check both paths using controlled clocks. | Completed | Desktop `gameplay/TurnTimer.java`, `gameplay/GameSessionTimingTest.java`. Online `rooms/IndependentGame.java`, `IndependentGameplayTest.java` |
| 33 | Decode HTML entities in answers | The parser and mapper decode text in the question and choices. Tests cover encoded answer characters. | Completed | Desktop `api/OpenTdbQuestionMapper.java`, `api/QuestionParserTest.java` |
| 34 | Test a damaged or locked database | Tests cover corrupt and locked databases and rollback after a failed migration. | Completed | Desktop `db/DatabaseTest.java` |

## Eight Reported Bugs

| No. | Requirement | Implementation | Status | Reference |
|---:|---|---|---|---|
| 1 | Prevent full credit after wrong attempts | An incorrect attempt reduces the points available for a later correct answer. | Completed | Desktop `gameplay/GameEngine.java`, `gameplay/GameEngineTest.java` |
| 2 | Avoid timeout records for eliminated players | Reaching zero extra chances does not eliminate a player. | Completed | Desktop `gameplay/NewGameScreen.java`, `gameplay/GameSessionTimingTest.java` |
| 3 | Keep setup queries off the UI thread | Game setup and filtered counts use background tasks. Live load verification remains. | Partially Completed | Desktop `gameplay/NewGameScreen.java`, `db/QuestionDao.java` |
| 4 | Keep players with identical names distinct | Scoring, aggregation, and import use stable IDs. | Completed | Desktop `service/LeaderboardService.java`, `db/AnalyticsDao.java`, `db/ScoreIdentityRoundTripTest.java` |
| 5 | Count records without loading full tables | Dashboard service uses DAO count queries in a task. | Completed | Desktop `service/DashboardService.java`, `home/Home.java` |
| 6 | Avoid empty sessions before play | The desktop session is created when the first answer is committed. | Completed | Desktop `gameplay/GameSession.java`, `gameplay/GameSessionTimingTest.java` |
| 7 | Avoid loading the chart twice on refresh | Refresh guards against triggering the selection listener a second time. | Completed | Desktop `analytics/AnalyticsScreen.java` |
| 8 | Remove duplicate screens and automatic downloads | The desktop has one game screen. Users explicitly download from the home screen. | Completed | Desktop `home/Navigator.java`, `home/Home.java` |

## Remaining Issues and Checks

| No. | Requirement | Implementation | Status | Reference |
|---:|---|---|---|---|
| 1 | Complete desktop MVC and Java cleanup | Controllers, services, and background tasks are in place. The remaining separation and style issues are listed in corrections 2, 3, 21, and 23. | Partially Completed | Desktop `gameplay/GameSession.java`, `gameplay/NewGameScreen.java` |
| 2 | Support identical online category names in separate branches | The current unique key is owner plus normalized name. The repository rejects the same name under another parent. | Pending | Online `bank/PersonalQuizzes.java`, V7 and V13 migrations |
| 3 | Open older room lobbies with incomplete snapshots | An older lobby without all stored questions cannot start. Create a new room to continue. Existing historical results remain readable. | Pending | Online `rooms/RoomService.java`, `RoomQuestionSourcesTest.java` |
| 4 | Verify desktop layout and responsiveness on a real display | Automated tests cover underlying operations. Control alignment, scaling, contrast, and window sizing still need inspection. | Pending | Week 11 review §7, [manual checks](TESTING.md#manual-verification) |
| 5 | Verify public room play and real email delivery | Automated tests verify room operations and capture email. Public HTTPS gameplay and delivery to a real inbox still need verification. | Pending | [Manual checks](TESTING.md#manual-verification) |
| 6 | Match the official final document and defense format | The current documents follow the available review. Obtain the standalone design and defense templates and the Week 11 grading and design checklists. The final defense PowerPoint is a separate deliverable. | Pending | Professor reference list, [design document](design-document.md) |

The desktop database migration can refuse inconsistent historical rows or unsupported custom schema changes. Keep the original SQLite file available for review if migration stops. Mailjet acceptance records a provider response. Actual delivery depends on the provider and recipient.

## Design Questions from the Professor

| Question | Answer |
|---|---|
| Which desktop screen is used for the final game? | `Navigator` opens `NewGameScreen`. |
| Why did desktop scores contain both a category and a question? | The category was redundant. Scores now link to the question and derive the category from it. |
| Why are there synchronization IDs? | The desktop importer uses them to identify the same player, session, or score across exports. |
| What data does the host need between sessions? | Online hosts use their account, saved quizzes, and room history. Room passwords are stored as hashes. The desktop game stores local players and results in SQLite. |
