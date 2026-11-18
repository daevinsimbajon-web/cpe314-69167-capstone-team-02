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

Hootly Online lets a host run trivia games while players join from their own devices. The host chooses questions, creates a room, and shares its code. Players answer timed questions, use optional retry chances, and view their results after the game. Hosts and players can return to their game history, and players can review their statistics.

## Features

- Register, verify your email, and sign in. Email codes also support password recovery.
- Create multiplayer rooms and invite players through a room code or link.
- Choose OpenTDB questions with Instant Quiz or use a saved quiz from My Quizzes.
- Create, edit, preview, duplicate, import, and practice personal quizzes.
- Set question timers, retry chances, and whether answers are revealed.
- View final rankings, personal results, and game history.
- Track points, accuracy, and progress through personal analytics.
- Export and import statistics as JSON to compare players.
- Request automatic statistics updates, with permission from the other player.

## Requirements

- JDK 17 or later and Apache Maven 3.9.
- Node.js 22.12 or later in the Node 22 release line, with npm.
- PostgreSQL 17, installed locally or through the included Docker Compose setup.
- Docker with Compose for the container setup and PostgreSQL integration tests.
- Mailjet credentials, a verified sender address, and an OTP secret for account emails.

## Technologies Used

| Area | Technology |
|---|---|
| Frontend | React, TypeScript, and Vite |
| Backend | Java, Spring Boot, Spring Security, REST, and WebSockets |
| Online storage | PostgreSQL, JDBC/JPA, Flyway, and Spring Session JDBC |
| Question API and JSON | Open Trivia Database, Java HTTP Client, and Jackson |
| Email | Mailjet REST API by default, with SMTP as a separate configuration option |
| Testing | JUnit 5, Testcontainers, Vitest, Testing Library, and Playwright |
| Desktop application | JavaFX, SQLite, and JDBC |

## Build and Run

Open PowerShell in the project folder.

### Environment configuration

Create `online/.env` from the example when setting up the application for the first time.

```powershell
if (-not (Test-Path online/.env)) {
    Copy-Item online/.env.example online/.env
}
```

Set `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` in `online/.env`. The database name in `DATABASE_URL` must match `POSTGRES_DB`. The default address is `jdbc:postgresql://localhost:5432/quiznight`. Use the existing credentials when connecting to a database that already contains data.

For email verification and password recovery, set `EMAIL_PROVIDER=mailjet`, `MAILJET_API_KEY`, `MAILJET_SECRET_KEY`, and a verified `MAILJET_FROM_EMAIL`. Set `OTP_HMAC_SECRET` to a Base64 value generated from at least 32 random bytes. Store credentials in the ignored `online/.env` file.

### PostgreSQL and backend

```powershell
docker compose --env-file online/.env -f online/docker-compose.yml up -d
powershell -NoProfile -File online/scripts/Start-Backend.ps1
```

The backend script loads `online/.env` and starts the API at `http://127.0.0.1:8080`. Flyway applies database migrations during startup.

### Frontend

In a separate terminal, run:

```powershell
cd online/frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite forwards API and WebSocket requests to the backend. If npm is unavailable and the portable Node installation is present, run `. ./online/scripts/Use-PortableNode.ps1` from the project folder before entering `online/frontend`.

### Access from different devices

Forward port 5173 through a public HTTPS address that supports WebSockets. In `online/.env`, set `FRONTEND_URL` and `ALLOWED_ORIGINS` to that address and set `COOKIE_SECURE=true`. Restart the backend after changing these settings. Before starting the frontend, set `HOOTLY_PUBLIC_ORIGINS` to the same address.

```powershell
$env:HOOTLY_PUBLIC_ORIGINS = 'https://your-forwarded-host.example'
cd online/frontend
npm run dev
```

Keep the backend and database bound to localhost. Players can open the public HTTPS address on their devices and join with the room code. HTTPS is needed for browser features used during gameplay and room sharing.

### Tests and production build

```powershell
mvn test
mvn -f online/backend/pom.xml test
mvn -f online/backend/pom.xml verify
cd online/frontend
npm test
npm run build
```

Desktop tests use a separate SQLite file. Backend tests use H2, and `verify` also runs the PostgreSQL integration tests in temporary containers. Email tests use mocked delivery or a local mail capture. Browser test instructions and recorded results are in [Testing](doc/TESTING.md).

## Database

Hootly Online stores accounts, quizzes, room activity, results, and shared statistics in PostgreSQL.

| Purpose | Tables |
|---|---|
| Accounts | `users`, `account_email_challenges`, `password_reset_grants`, `account_email_deliveries` |
| Private quizzes | `quiz_collections`, `quiz_collection_questions`, `questions`, `categories`, `personal_quiz_categories` |
| Multiplayer | `quiz_rooms`, `room_participants`, `room_questions`, `game_sessions`, `answers`, `answer_attempts`, `scores` |
| Statistics sharing | `compared_players`, `compared_games`, `statistics_sharing`, `statistics_import_jobs` |

The [database diagrams](doc/ERD.md) show the full schemas, supporting tables, and constraints. The JavaFX desktop application stores its data in a separate SQLite file.

## Question Sources

1. Instant Quiz fetches questions from OpenTDB using the host’s selected filters.
2. My Quizzes uses a saved quiz created by the host.

Hootly saves a copy of the selected questions in each room. Later quiz edits leave that room’s questions intact.

## OpenTDB Limitations

- Instant Quiz accepts 1 to 50 questions. `amount` is required and `encode=url3986` is sent automatically.
- Optional filters are `category` (an official category ID), `difficulty` (`easy`, `medium`, or `hard`), and `type` (`multiple` or `boolean`). An `any` selection omits that filter.
- OpenTDB supplies the answer choices. True/false questions have two choices. Multiple-choice questions use the correct answer and incorrect choices returned by the API.
- Hootly spaces question requests five seconds apart and makes up to three attempts to collect enough distinct questions.
- A saved category list can be used during a category API outage. If questions are unavailable or too few match the filters, the host can change the filters or use My Quizzes.

## JavaFX Desktop Application

The JavaFX desktop application supports local trivia games, score import and export, and analytics. It uses `data/trivia.db` for SQLite storage. Launch it from the project folder.

```powershell
mvn javafx:run
```

The main entry point is `com.triviagame.App`.

## Known Issues and Limitations

- Instant Quiz requires an OpenTDB connection.
- An older lobby with incomplete saved questions needs to be recreated.
- Personal category names must be unique within an account, including categories under different parents.
- Actual email delivery and play across two physical devices still need checking.

See [Requirements and known issues](doc/KNOWN_ISSUES.md) for the full list and current status.

## Documentation

- Project design document V1.0 ([DOCX](doc/Project_Design_Document.docx), [PDF](doc/Project_Design_Document.pdf), [Markdown](doc/design-document.md))
- [PostgreSQL and SQLite ERDs](doc/ERD.md)
- [Requirements and known issues](doc/KNOWN_ISSUES.md)
- [Testing commands and results](doc/TESTING.md)
- [Original project proposal](doc/proposal.md)
