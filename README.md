# Hootly — Trivia & Quiz Night Manager

## Project Information

Project Title: Trivia & Quiz Game Night Manager

Application Name: Hootly

Course: CPE 314 — Software Design

Section: 3A

Team Number: 02

## Team Members

| No. | Name | Student ID Number | GitHub Username |
|---:|---|---|---|
| 1 | John Daevin S. Simbajon | 25245093 | [@daevinsimbajon-web](https://github.com/daevinsimbajon-web) |
| 2 | Justine B. Cuizon | 25245374 | [@justinecuizon0906-create](https://github.com/justinecuizon0906-create) |
| 3 | Joshua Neil G. Malaya | 25209073 | [@jmalaya7231-lgtm](https://github.com/jmalaya7231-lgtm) |
| 4 | Clarence Philip A. Elegino | 25260068 | [@philipelegino](https://github.com/philipelegino) |
| 5 | Gebrielle P. Mirasol | 25207549 | [@gebriellemirasol](https://github.com/gebriellemirasol) |

## Project Overview

Hootly Online is a multiplayer trivia application for classmates, friends, and community groups. A host selects a question source, creates a room, and shares its room code. Players join from their own browsers. In new rooms, the host starts and monitors the game while players answer at their own pace. Hootly records the results so participants can review their games and compare their progress.

## Features

- Create an account, verify your email with a code, and sign in. Email codes also support password recovery.
- Host multiplayer rooms and invite players by room code or invitation link.
- Fetch questions through Instant Quiz or host a ready quiz from My Quizzes.
- Create, edit, preview, duplicate, and practice private quizzes. Import questions into a private draft.
- Play timed questions with optional retry chances. The host can choose whether to reveal answers.
- View final standings and completed games in game history.
- Review personal statistics and charts based on saved results.
- Export personal statistics as JSON and import exports into Compare Players. Verified accounts can approve or revoke access to live updates.

## Requirements

- JDK 17 or later and Apache Maven 3.9.
- Node.js 22.12 or later in the Node 22 release line, with npm.
- PostgreSQL 17. The supplied Compose file uses the `postgres:17-alpine` image. A local PostgreSQL installation can also be used.
- Docker with Compose for the supplied database setup. Docker is also required for the PostgreSQL integration tests.
- A configured email provider and OTP secret for account verification and recovery. Automated tests use captured or mocked email.

## Technologies Used

| Area | Technology |
|---|---|
| Frontend | React, TypeScript, and Vite |
| Backend | Java, Spring Boot, Spring Security, REST, and WebSockets |
| Online storage | PostgreSQL, JDBC/JPA, Flyway, and Spring Session JDBC |
| Question API and JSON | Open Trivia Database, Java HTTP Client, and Jackson |
| Email | Mailjet REST API by default, with SMTP as a separate configuration option |
| Testing | JUnit 5, Testcontainers, Vitest, Testing Library, and Playwright |
| Retained desktop app | JavaFX, SQLite, and JDBC |

## Build and Run

Run these commands from the repository root in PowerShell unless a step changes the directory.

### Environment configuration

Create a local configuration from the example if `online/.env` does not already exist.

```powershell
if (-not (Test-Path online/.env)) {
    Copy-Item online/.env.example online/.env
}
```

Set `POSTGRES_USER` and `POSTGRES_PASSWORD` in `online/.env`. Make the database name in `DATABASE_URL` match `POSTGRES_DB`. For the default Compose setup, use `jdbc:postgresql://localhost:5432/quiznight`. Reuse the existing database credentials when starting an existing volume.

For account email, leave `EMAIL_PROVIDER=mailjet` and set `MAILJET_API_KEY`, `MAILJET_SECRET_KEY`, and a verified `MAILJET_FROM_EMAIL`. Generate a private `OTP_HMAC_SECRET` from at least 32 random bytes encoded as Base64. Keep the values in the ignored `online/.env` file.

### PostgreSQL and backend

```powershell
docker compose --env-file online/.env -f online/docker-compose.yml up -d
powershell -NoProfile -File online/scripts/Start-Backend.ps1
```

The start script loads `online/.env`. Flyway applies pending migrations and the backend listens at `127.0.0.1:8080` by default.

### Frontend

In a separate terminal, run:

```powershell
cd online/frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite forwards both `/api` and `/ws` to the backend. If you use the repository’s optional portable Node installation, run `. ./online/scripts/Use-PortableNode.ps1` from the root before changing directories.

### Access from different devices

Forward the frontend on port 5173 through an HTTPS endpoint that supports WebSockets. The Vite forwarding configuration recognizes a local Cloudflare Tunnel when its exact HTTPS origin is approved. Use that origin for `FRONTEND_URL` and `ALLOWED_ORIGINS` and set `COOKIE_SECURE=true` in `online/.env`. Set the same origin in the terminal that starts Vite:

```powershell
$env:HOOTLY_PUBLIC_ORIGINS = 'https://your-forwarded-host.example'
cd online/frontend
npm run dev
```

Keep the backend and database on loopback. Share the HTTPS URL and room code with players. Use the configured HTTPS URL on other devices so browser features such as generating submission IDs and copying room codes have a secure context.

### Tests and production build

```powershell
mvn test
mvn -f online/backend/pom.xml test
mvn -f online/backend/pom.xml verify
cd online/frontend
npm test
npm run build
```

The root Maven command runs the desktop suite with a test SQLite file. Backend `test` uses H2, and `verify` also runs the integration suite against disposable PostgreSQL containers. See [Testing](doc/TESTING.md) for browser tests and recorded results.

## Database

Hootly Online uses PostgreSQL. The main tables are grouped below.

| Purpose | Tables |
|---|---|
| Accounts | `users`, `account_email_challenges`, `password_reset_grants`, `account_email_deliveries` |
| Private quizzes | `quiz_collections`, `quiz_collection_questions`, `questions`, `categories`, `personal_quiz_categories` |
| Multiplayer | `quiz_rooms`, `room_participants`, `room_questions`, `game_sessions`, `answers`, `answer_attempts`, `scores` |
| Statistics sharing | `compared_players`, `compared_games`, `statistics_sharing`, `statistics_import_jobs` |

The full [ERDs](doc/ERD.md) include the supporting tables and constraints. SQLite belongs to the separate JavaFX desktop application.

## Question Sources

1. **Instant Quiz** requests fresh questions from OpenTDB. Hootly saves them in the room’s question snapshots.
2. **My Quizzes** uses the questions from a ready quiz owned by the host. Hootly copies the questions into the room so later edits to the quiz do not alter the game.

## OpenTDB Limitations

- Instant Quiz accepts 1 to 50 questions. `amount` is required and `encode=url3986` is sent automatically.
- Optional filters are `category` (an official category ID), `difficulty` (`easy`, `medium`, or `hard`), and `type` (`multiple` or `boolean`). An `any` selection omits that filter.
- OpenTDB controls the choices. True/false has two choices. Multiple choice has one correct answer and the incorrect options supplied by OpenTDB. Hootly does not request a custom number of choices.
- Hootly normally spaces question requests five seconds apart and makes up to three attempts to fill a room with distinct questions. It uses a saved category list when available during a category API outage. If there are too few matching questions or the question API is unavailable, room creation reports the problem. A saved quiz can still be hosted.

## JavaFX Desktop Application

The retained JavaFX application demonstrates the course’s JavaFX and SQLite requirements. It uses a local SQLite file, `data/trivia.db`, and runs separately from Hootly Online.

```powershell
mvn javafx:run
```

The main entry point is `com.triviagame.App`.

## Known Issues and Limitations

Instant Quiz depends on OpenTDB. Rooms from older versions that lack a complete question snapshot must be recreated. Online personal category names are unique across a user’s collection, including across different branches. The [requirements and known issues](doc/KNOWN_ISSUES.md) list the remaining implementation and demonstration checks.

## Documentation

- [Project design document](doc/design-document.md)
- [PostgreSQL and SQLite ERDs](doc/ERD.md)
- [Known issues and professor requirements](doc/KNOWN_ISSUES.md)
- [Testing commands and results](doc/TESTING.md)
- [Final defense outline](doc/FINAL_DEFENSE.md)
- [Original project proposal](doc/proposal.md)
