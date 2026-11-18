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

Results from the documentation revision on 18 November 2026:

| Check | Passed | Failed or errors | Skipped |
|---|---:|---:|---:|
| Desktop Maven tests | 188 | 0 | 0 |
| Online backend Maven tests | 115 | 0 | 0 |
| PostgreSQL integration tests | 53 | 0 | 0 |
| Vitest | 137 | 0 | 0 |
| Playwright | 2 | 0 | 0 |

The frontend production build passed. Vite reported existing warnings about bundle size and a module imported both statically and dynamically. Database diagrams were checked against a freshly initialized SQLite file and a disposable PostgreSQL catalog migrated through V16.

| Area | Examples of coverage |
|---|---|
| Desktop game and database | `GameEngineTest`, `GameSessionTimingTest`, `DatabaseTest`, and `ScoreSchemaMigrationTest` cover scoring, undo and timing, database failure, and preserving historical data. |
| Online rooms and question sources | `IndependentGameplayTest`, `RoomQuestionSourcesTest`, `OpenTriviaTest`, and `PersonalQuizTest` cover timing, retries, snapshots, saved quiz access, and provider failures. |
| Accounts and comparisons | `EmailSecurityTest` covers verification and recovery with captured mail. `StatisticsSharingTest` covers export signatures, permissions, revocation, and renewed consent. |
| Frontend | Vitest covers sign-in, room setup, the quiz editor, retry prompts, and statistics comparisons. Playwright checks two browser-based visuals and interactions. |

## Manual Verification

- Complete a room across two devices using the configured public HTTPS endpoint.
- Verify account email delivery with the configured provider and an approved test recipient.
- Check JavaFX layout and responsiveness on the type of display used for the defense.
- Rehearse room setup, timed play, and results. Each team member should prepare an explanation of their work. [Individual contributions](design-document.md#9-individual-contributions)
