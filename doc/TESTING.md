# Hootly Testing

## Commands

From the repository root:

```powershell
mvn test
mvn -f online/backend/pom.xml test
mvn -f online/backend/pom.xml verify
```

The desktop suite uses the SQLite path set by the root Maven configuration. Backend tests use the test profile and captured email. The `verify` command also runs integration tests with disposable PostgreSQL containers. Start Docker before running `verify`.

For the frontend:

```powershell
cd online/frontend
npm ci
npm test
npm run build
```

The two current Playwright specs exercise the landing page and mascot controls. To run them, start Vite in a separate terminal on the test address required by the Playwright configuration:

```powershell
cd online/frontend
npx vite --host 127.0.0.1 --port 5174
```

Then run these commands from `online/frontend` in another terminal:

```powershell
$env:HOOTLY_E2E_BASE_URL = 'http://127.0.0.1:5174'
npx playwright install chromium
npm run test:e2e
```

The browser checks in the recorded run used installed Microsoft Edge through `PLAYWRIGHT_CHANNEL=msedge`. The landing and mascot specs do not need a live database. Use an isolated backend and database for browser tests that create accounts or games.

## Recorded Results

Results from isolated runs on 18 November 2026.

| Check | Classes or files | Passed | Failed or errors | Skipped |
|---|---:|---:|---:|---:|
| Desktop Maven tests | 25 | 189 | 0 | 0 |
| Online backend Maven tests | 30 | 117 | 0 | 0 |
| PostgreSQL integration tests | 9 | 53 | 0 | 0 |
| Vitest | 22 | 137 | 0 | 0 |
| Playwright | 2 | 2 | 0 | 0 |

The desktop and backend suites passed after the package and exception-handling changes. Frontend tests and the production build passed on the same day. Vite reported a bundle larger than 500 kB and a module imported both statically and dynamically.

JaCoCo measured 50.5% line and 49.9% branch coverage for desktop production Java after the controller and package changes. Backend coverage is recorded in [design document section 8](design-document.md#8-testing-summary), along with one executed edge case per JUnit class.

The database diagrams match an initialized SQLite file and a PostgreSQL 17 database migrated through V16. The integration tests passed with the same migrations. All 15 Mermaid diagrams rendered in Edge.

| Area | Examples of coverage |
|---|---|
| Desktop game and database | `GameEngineTest`, `GameSessionTimingTest`, `DatabaseTest`, and `ScoreSchemaMigrationTest` cover scoring, undo and timing, database failure, and preserving historical data. |
| Online rooms and question sources | `IndependentGameplayTest`, `RoomQuestionSourcesTest`, `OpenTriviaTest`, and `PersonalQuizTest` cover timing, retries, invalid snapshot positions, saved quiz access, and provider failures. |
| Accounts and comparisons | `EmailSecurityTest` covers verification and recovery with captured mail. `StatisticsSharingTest` covers export signatures, permissions, revocation, and renewed consent. |
| Frontend | Vitest covers sign-in, room setup, the quiz editor, retry prompts, and statistics comparisons. Playwright checks two browser-based visuals and interactions. |

## Manual Verification

### Local checks completed

- Two independent Edge sessions used a disposable backend and database. Both verified registration through a local SMTP sink, with the exact addresses associated with the test accounts.
- The host created a room from My Quizzes and the player joined with its code. The browser showed the five-second retry prompt and the paused timer. The correct retry earned seven points, the chance counter reached zero, and both users saw final standings and history.
- Instant Quiz created a room from a live OpenTDB response with one question.
- A signed statistics export imported successfully. The comparer saw the sharing disconnect without a browser refresh. The recipient could deny a new request, and the denial cooldown was enforced.
- The same accounts, results, and statistics were available after restarting only the temporary backend.
- The JavaFX startup and game setup screens rendered at 1280 × 800. The game setup loaded its saved-question count and kept navigation accessible.
- A separate 2,000-record SQLite import completed in 18.688 seconds while JavaFX processed 670 visible label updates.

### Checks to complete

- Complete a room across two physical devices using the public HTTPS forwarding address.
- Confirm delivery through the configured email provider using an approved recipient with access to the inbox.
- Check JavaFX layout and responsiveness on the presentation display.
- Test the complete demonstration flow and review each member’s contribution. [Individual contributions](design-document.md#9-individual-contributions)
