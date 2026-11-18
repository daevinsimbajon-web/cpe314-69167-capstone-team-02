# Hootly — Trivia & Quiz Night Manager

Hootly Online lets a host run trivia rooms while players answer on their own devices. The app supports retry chances, game history, personal analytics, and consent-based comparison of exported statistics.

Hosts choose one of two question sources:

- **Instant Quiz** fetches fresh questions from Open Trivia Database (OpenTDB) when the host creates a room. If the provider cannot supply the requested questions, room creation reports the error.
- **My Quizzes** uses a ready quiz saved to the host’s private collection. It works independently of OpenTDB.

Both sources save a question snapshot at room creation. Existing snapshots and their historical records remain readable. A legacy lobby with missing snapshots needs to be recreated using one of the two current sources.

**Team 02, CPE 314 section 3A:** John Daevin S. Simbajon, Clarence Philip A. Elegino, Justine B. Cuizon, Joshua Neil G. Malaya, and Gebrielle P. Mirasol.

## Technology and requirements

- JDK 17 or later and Maven 3.9.
- Node.js 22 and npm; React, TypeScript and Vite for the frontend.
- PostgreSQL 17 (or Docker with Compose) for online data; Spring Boot, JDBC/JPA and Flyway for the backend.
- The desktop application uses JavaFX, SQLite and JDBC. Maven supplies JavaFX dependencies.
- Docker is required for the isolated PostgreSQL integration tests.

## Configure and start Hootly Online

From the repository root in PowerShell, copy the example only if you do not already have `online/.env`:

```powershell
Copy-Item online/.env.example online/.env
```

Edit `online/.env` privately. Set `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `DATABASE_URL` to your PostgreSQL database; the Compose configuration initializes a new volume using `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`. Changing these variables later does not change credentials inside an existing PostgreSQL volume. Never delete an existing volume just to restart the app.

For account email codes, generate `OTP_HMAC_SECRET` as Base64 containing at least 32 cryptographically random bytes. Configure `EMAIL_PROVIDER=mailjet`, `MAILJET_API_KEY`, `MAILJET_SECRET_KEY`, and a verified `MAILJET_FROM_EMAIL`; `MAILJET_FROM_NAME` defaults to Hootly. Mailjet acceptance does not guarantee delivery. Keep these values out of Git. The configured SMTP transport is an optional alternative; do not configure both as simultaneous senders. Without email and OTP configuration, email verification cannot be delivered.

Start PostgreSQL if necessary, preserving any existing database:

```powershell
docker compose --env-file online/.env -f online/docker-compose.yml up -d
```

In a separate terminal, start the backend. This script loads `online/.env` and then runs Spring Boot. Flyway applies pending migrations; the backend defaults to `127.0.0.1:8080`.

```powershell
powershell -NoProfile -File online/scripts/Start-Backend.ps1
```

In another terminal, start the frontend:

```powershell
cd online/frontend
npm install
npm run dev
```

Visit `http://localhost:5173`. The existing Vite proxy forwards `/api` and `/ws` to the backend. If Node is not on PowerShell's PATH and the optional portable installation exists, run `. ./online/scripts/Use-PortableNode.ps1` from the repository root before starting the frontend.

## Public access

Use an HTTPS-capable public port-forwarding service and forward its public endpoint to the Vite frontend on port 5173. Keep PostgreSQL and the backend on loopback. Put the exact public HTTPS origin in `FRONTEND_URL` and `ALLOWED_ORIGINS` in `online/.env`; set `COOKIE_SECURE=true` for HTTPS. Before starting Vite, set `HOOTLY_PUBLIC_ORIGINS` to that exact origin, for example:

```powershell
$env:HOOTLY_PUBLIC_ORIGINS='https://your-public-host.example'
cd online/frontend
npm run dev
```

Restart the corresponding application process after changing its configuration. The forwarded origin must be trusted by the proxy configuration for correct HTTPS cookies, API requests, and WebSockets. Do not use a wildcard host allowance. Do not expose the backend or database directly.

## Tests

Tests use isolated databases and mock/capture email delivery; they do not require real test emails.

```powershell
# Desktop, isolated SQLite
mvn test

# Online unit/HTTP tests and disposable PostgreSQL integration tests (Docker required)
mvn -f online/backend/pom.xml verify

# Frontend
cd online/frontend
npm test
npm run build
```

PostgreSQL integration tests use Testcontainers and require Docker plus the `postgres:17-alpine` image. Avoid `clean` or volume removal against running services.

## Legacy JavaFX desktop

From the repository root:

```powershell
mvn javafx:run
```

The entry point is `com.triviagame.App` and the default SQLite file is `data/trivia.db`. This retained application supplies the course-required JavaFX and SQLite implementation. The online build reuses selected Java models and API classes without loading the desktop GUI, SQLite storage, or bundled questions. See [design document](doc/design-document.md), [requirement map](doc/KNOWN_ISSUES.md), and [defense outline](doc/FINAL_DEFENSE.md).
