# Known issues and requirement-to-code map

Audit of `project/milestone-3`, 18 November 2026. The numbering below follows the 34 corrections in `professor-references/Week11_Check_A2.pdf`, not that review's old status. “Partial” means some evidence exists but the complete instruction or a manual defense check remains. Paths are repository-relative. This is a source/test review, not proof of a live public deployment or real email delivery.

Hootly Online now creates rooms through Instant Quiz or a ready quiz from My Quizzes. `RoomService.java` requires room snapshots at start. Older lobbies with incomplete snapshots must be recreated. Existing shared-bank records, historical game snapshots, and import logs remain in PostgreSQL. The archived online bank endpoints and online starter-bank loading have been retired.

## Current remaining issues

1. Desktop MVC is improved but incomplete: `src/main/java/com/triviagame/gameplay/GameSession.java` still creates DAOs; there is no dedicated controller per screen and DAO classes remain in `db`. Avoid claiming complete layer isolation.
2. Java standards are incomplete across the tree: long methods/files, compact formatting and some broad exception handling remain. The two newly identified desktop catches were corrected in this audit; a full standards cleanup is not claimed.
3. The SQLite ERD records the eight desktop tables and their columns; the online ERDs show core and support relationships but omit non-key columns. Exact definitions and constraints are in `src/main/java/com/triviagame/db/Database.java`, `SessionPlayerDao.java`, `TimedAnswerDao.java`, `service/QuestionBankService.java`, and `online/backend/src/main/resources/db/migration/`.
4. The standalone final-defense template and the Week 11 full checklist are not supplied. `professor-references/README.txt` identifies them as missing. `doc/FINAL_DEFENSE.md` is content for a deck, not the professor's verified template or a finished slide file.
5. Team members must confirm individual work and demonstrate their own contribution. Public-access gameplay, real OTP email delivery and the final live demo were not exercised by this audit; do not present automated results as evidence of real delivery.
6. The desktop score-category migration intentionally refuses certain conflicting legacy data linked to timed answers, or custom schema dependencies, rather than losing records. Back up and investigate such a database; do not reset it.
7. Current online Mailjet and optional SMTP are mutually exclusive provider settings. Configuration and sender verification determine real delivery; provider acceptance alone is not proof of delivery.
8. PostgreSQL personal quiz categories retain `UNIQUE(owner_user_id, normalized_name)` from `online/backend/src/main/resources/db/migration/V7__personal_quiz_collections.sql`; V13 adds a parent but does not change that constraint. Consequently identical names under different parents are currently rejected for one owner. The SQLite sibling rule is separate. Changing online identity semantics requires an additive migration and corresponding repository changes; no existing records were modified in this audit.
9. The instructor's prior desktop visual findings (control alignment, scaling, contrast and window sizing) have not been checked in a live JavaFX session during this audit. They cannot be marked fixed from automated test results alone; inspect the current UI against Week 11 review §7 before the defense.

## Nine mandatory technical requirements

| # | Requirement | Current implementation and evidence | Status |
|---|---|---|---|
| 1 | OOP, inheritance, runtime polymorphism | `questions/Question.java`, `MultipleChoiceQuestion.java`, `TrueFalseQuestion.java`, `CategoryQuestion.java`, `ScienceQuestion.java`, `HistoryQuestion.java`, `EntertainmentQuestion.java`; `questions/CategoryQuestionTest.java`. Category decorators vary points. | Implemented; demonstrate |
| 2 | Queue, stack, map and recursion | `gameplay/GameEngine.java` FIFO deque and scored-answer LIFO undo; `gameplay/NewGameScreen.java` Undo; `category/Category.java`, `db/CategoryDao.java`; `service/LeaderboardService.java`; `GameEngineTest`, `CategoryDaoTest`. | Implemented; demonstrate |
| 3 | JUnit normal and edge cases | `src/test/java/`, `online/backend/src/test/java/`; desktop test summary below. | Implemented |
| 4 | SQLite, JDBC, prepared statements | `db/Database.java`, `db/ScoreDao.java`, `db/ScoreSchemaMigrationTest.java`, `pom.xml`. PostgreSQL is an additional online store. | Implemented |
| 5 | Public REST and JSON | `api/OpenTdbClient.java`, `api/dto/`, `api/OpenTdbResponseParser.java`, `api/OpenTdbQuestionMapper.java`, `api/OpenTdbClientTest.java`, `api/OpenTdbContractTest.java`. | Implemented; network availability limits live demo |
| 6 | Event-driven JavaFX GUI | `App.java`, `home/Home.java`, `gameplay/NewGameScreen.java`, `analytics/AnalyticsScreen.java`. | Implemented; demonstrate |
| 7 | Background concurrency | `App.java` owns executor; `tasks/FetchQuestionsTask.java`, `importer/ImportScreen.java`, `online/.../analytics/StatisticsImportJobs.java`; `tasks/FetchQuestionsTaskTest.java`. | Implemented; demonstrate responsiveness |
| 8 | Mining from stored records | `db/AnalyticsDao.java`, `service/AnalyticsService.java`, `online/.../analytics/AnalyticsRepository.java`; `AnalyticsInsightsTest.java`. | Implemented |
| 9 | Labeled visualizations | `analytics/AnalyticsScreen.java`, `online/frontend/src/analytics/Charts.tsx`; analytics tests. | Implemented; demonstrate |

All desktop paths below are under `src/main/java/com/triviagame/`, unless another root is explicit. Test paths are under `src/test/java/com/triviagame/`.

## All 34 professor corrections

| # | Requirement | Current implementation, exact evidence and verification | Status |
|---|---|---|---|
| 1 | Known issues and requirement map | This document; Week 11 reference §2. | Completed |
| 2 | Controllers; views avoid direct DAO/SQL | `controller/GameController.java`, `service/` and JavaFX screens; `gameplay/GameSession.java` still constructs DAOs and other screens use services directly. | Partial |
| 3 | Organize model/DTO/service/DAO/controller/UI; move QuestionBankService | `service/QuestionBankService.java`, `api/dto/`, `controller/GameController.java`; DAOs still under `db`, desktop package layout is not the proposed complete layout. | Partial |
| 4 | Consolidate entry points and game screens | `App.java`, `home/Navigator.java`, `gameplay/NewGameScreen.java`; old `m2` and duplicate screen files are absent from current tree. `db/SampleQuestionSeeder.java` is a utility. | Completed for main app |
| 5 | Remove milestone-specific package/screen names | `gameplay/NewGameScreen.java`; no `com.triviagame.m2` in current source tree. | Completed |
| 6 | Move DB reads off JavaFX thread | `home/Home.java`, `players/Players.java`, `results/Results.java`, `analytics/AnalyticsScreen.java`, `gameplay/NewGameScreen.java` use `Task` and workers; no interactive profiling in this audit. | Partial; live responsiveness pending |
| 7 | Managed executor, UI updates on FX thread | `App.java` owns executor and shuts it down; screen `Task` handlers update UI. | Completed for reviewed flows |
| 8 | Synchronous score importer; controller/task owns worker | `db/ScoreJsonImporter.java`; `importer/ImportScreen.java` schedules a Task. | Completed |
| 9 | API client, background task and controller tests | `api/OpenTdbClientTest.java`, `tasks/FetchQuestionsTaskTest.java`, `controller/GameControllerTest.java`; actual suite results below. | Completed |
| 10 | Edge cases for every test class | Extensive normal/boundary coverage including `db/DatabaseTest.java`; exhaustive per-class coverage not independently established. | Partial |
| 11 | Accurate complete ERD, all columns/FKs | `doc/design-document.md` §4 includes the eight desktop tables and columns; online support tables and all check constraints remain in the schema sources. | Completed for desktop; online overview partial |
| 12 | Scores-to-questions cardinality | SQLite `Database.java` makes `scores.question_id` NOT NULL; §4 ERD shows exactly one question per score. | Completed |
| 13 | Final ERD in design document; remove placeholder | `doc/design-document.md` §4 includes the local desktop Mermaid ERD and the outdated placeholder is gone. | Completed |
| 14 | Describe many-to-many link and real unique keys | §4 ERD and `db/SessionPlayerDao.java` show `session_players` and composite key; review all constraints using schema source. | Completed for core relationships |
| 15 | Real SQL or link to real schema | `doc/design-document.md` §4 references exact schema sources and Flyway migrations. | Completed |
| 16 | Remove redundant scores.category_id | `db/Database.java` migrates, `db/ScoreDao.java` and `db/AnalyticsDao.java` derive category; `db/ScoreSchemaMigrationTest.java`. | Completed with documented refusal cases |
| 17 | Remove obsolete week references from design text | `doc/design-document.md` V1.0 describes current state; instructor historical review is identified as historical. | Completed |
| 18 | Document OpenTDB query parameters | `api/OpenTdbClient.java` and `doc/design-document.md` §5. | Completed |
| 19 | Describe all background operations accurately | `doc/design-document.md` §6; `App.java`, `tasks/FetchQuestionsTask.java`, `importer/ImportScreen.java`, `online/.../analytics/StatisticsImportJobs.java`, `online/.../mail/EmailDelivery.java`. | Partial; live profiling not performed |
| 20 | Finish concurrency verification | Automated Task and controller tests run; live GUI responsiveness and public demo still need manual verification. | Partial |
| 21 | Log technical errors and show safe messages | `gameplay/NewGameScreen.java` uses `reportFailure`; `api/QuestionParser.java` no longer silently catches the encoding conversion. Broad catches remain in other modules; not all error paths reviewed. | Partial |
| 22 | Remove duplicate semicolon and tab problems | `gameplay/GameEngine.java` has normal deque declarations; no tabs were found in `db/CategoryDao.java` or `db/QuestionDao.java`. Other formatting is tracked under item 23. | Completed |
| 23 | Braces, specific exceptions, size, Javadoc and constants | Some improvements exist; `gameplay/NewGameScreen.java` and compact online services still need a standards sweep. | Partial |
| 24 | API DTOs, strict parse/map, result type and saved fixture | `api/dto/OpenTdbResponse.java`, `api/dto/OpenTdbQuestionDto.java`, `api/OpenTdbResponseParser.java`, `api/OpenTdbQuestionMapper.java`, `api/OpenTdbResult.java`, `src/test/resources/opentdb/live-response-2026-11-17.json`, `api/OpenTdbContractTest.java`; legacy local import parser still supports historical formats. | Completed for OpenTDB |
| 25 | User-based design and authorization | `online/.../auth/AuthController.java`, `auth/SecurityConfiguration.java`, `rooms/RoomService.java`, `analytics/AnalyticsController.java`; host/player are room roles. Password hashing algorithm differs from the original proposal; manual two-device demonstration is pending. | Partial |
| 26 | Answer four team design questions | `doc/FINAL_DEFENSE.md` discussion prompts; actual answers must be delivered and confirmed by team. | Partial |
| 27 | Each member explains own contributions | Roles in `doc/design-document.md` §2 and §9; individual claims must be verified and presented by each member. | Partial |
| 28 | Describe host/player relationship and user | `doc/design-document.md` §§1, 3, 6; `rooms/RoomService.java`. | Completed |
| 29 | Document offline, fewer questions, HTML text | `doc/design-document.md` §5; `api/OpenTdbClient.java`, `api/OpenTdbQuestionMapper.java`, `api/OpenTdbClientTest.java`, `api/QuestionParserTest.java`. | Completed |
| 30 | Category sibling uniqueness and player names | `db/Database.java` sibling triggers, `db/CategoryDaoTest.java`; players use IDs, duplicate display names permitted; `db/ScoreIdentityRoundTripTest.java`. | Completed |
| 31 | Question creation time and external ID | `db/Database.java`, `db/QuestionDao.java`, `db/QuestionImportTest.java`; SQLite ERD §4. | Completed |
| 32 | Engine-owned timer, fake clock | `gameplay/GameSession.java`, `gameplay/TurnTimer.java`, `gameplay/GameSessionTimingTest.java`; online `rooms/IndependentGame.java`, `IndependentGameplayTest.java`. | Completed |
| 33 | Decode answers including apostrophe/ampersand | `api/QuestionParser.java`, `api/OpenTdbQuestionMapper.java`, `api/QuestionParserTest.java`. | Completed |
| 34 | Corrupt/locked database failure tests | `db/DatabaseTest.java` tests corrupt, locked and failed-migration rollback with isolated files. | Completed |

## Eight bugs in Week 11 review

| # | Reported bug | Status and evidence |
|---|---|---|
| 1 | Wrong answers then full credit | Fixed in reviewed flow: `gameplay/NewGameScreen.java` records incorrect attempts; `gameplay/GameEngine.java` applies reduced score; `GameEngineTest.java`. |
| 2 | Eliminated players get fake timeout rows | Fixed in reviewed flow: chances are retries, not elimination; players can continue with zero. `gameplay/NewGameScreen.java`, `GameSessionTimingTest.java`. |
| 3 | Setup database work blocks FX thread | Fixed for reviewed setup: `gameplay/NewGameScreen.java` uses background Tasks; `db/QuestionDao.java` has filtered COUNT. Manual load profiling remains. |
| 4 | Duplicate names merge players | Fixed: stable player IDs in `service/LeaderboardService.java` and `db/AnalyticsDao.java`; `db/ScoreIdentityRoundTripTest.java`. |
| 5 | Dashboard loads full tables for counts | Fixed: `service/DashboardService.java`, `db/QuestionDao.java` and other DAO COUNT methods; `home/Home.java` background Task. |
| 6 | Empty session created before play | Fixed: `gameplay/GameSession.java` creates session at first committed answer; `gameplay/GameSessionTimingTest.java`. |
| 7 | Analytics chart loads twice | Fixed in reviewed refresh: `analytics/AnalyticsScreen.java` guards selection listener with `updatingSelection`. |
| 8 | Dead/duplicate screens and automatic downloads | Fixed for identified files: current source has one desktop game screen, no `m2` package, and `home/Home.java` exposes explicit fetch. Utility `db/SampleQuestionSeeder.java` remains intentionally. |

## Test and operational evidence

- Desktop Maven: 188 tests, 0 failures, 0 errors, 0 skipped (18 November 2026; rerun after desktop error-handling edits is recorded in `target/final-desktop-test.log`).
- Frontend after the source cleanup: 137 Vitest tests and 2 Playwright tests passed with zero failures or skips. The production build succeeded.
- Backend after the source cleanup: 115 Maven tests and 53 disposable PostgreSQL integration tests passed with zero failures, errors, or skips.
- `online/backend/src/test/java/com/triviagame/online/StatisticsSharingTest.java` uses multiple isolated authenticated accounts and tests authorization, import signatures, revoke, renewed consent, denial and cooldown. `IndependentGameplayTest.java` covers exported timeout results and import. No production database records or real email are needed for these tests.
- Public port forwarding and actual mail delivery were not tested or restarted in this audit.
