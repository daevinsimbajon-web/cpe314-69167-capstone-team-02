# CPE314-Group2-S-Final-Project

## **Project Information**

**Project Title**: Hootly — Trivia & Quiz Night Manager

**Course**: CPE 314 — Software Design

**Section**: 3A

**Team Number**: 02

## **Team Members**

| No. | Name | Student ID Number | GitHub Username |
|---:|---|---|---|
| 1 | John Daevin S. Simbajon | 25245093 | [@daevinsimbajon-web](https://github.com/daevinsimbajon-web) |
| 2 | Justine B. Cuizon | 25245374 | [@justinecuizon0906-create](https://github.com/justinecuizon0906-create) |
| 3 | Joshua Neil G. Malaya | 25209073 | [@jmalaya7231-lgtm](https://github.com/jmalaya7231-lgtm) |
| 4 | Clarence Philip A. Elegino | 25260068 | [@philipelegino](https://github.com/philipelegino) |
| 5 | Gebrielle P. Mirasol | 25207549 | [@gebriellemirasol](https://github.com/gebriellemirasol) |

## Overview

Hootly lets a host run online trivia rooms while players answer on their own devices. It supports custom quizzes, Open Trivia Database questions, retry chances, results, personal analytics, and consent-based comparison of exported statistics.

The legacy JavaFX/SQLite desktop application remains in this repository for the CPE 314 requirements.

## Requirements

- JDK 17 or later and Maven 3.9.
- Node.js 22 and npm; React, TypeScript and Vite for the frontend.
- PostgreSQL 17 (or Docker with Compose) for online data; Spring Boot, JDBC/JPA and Flyway for the backend.
- The desktop application uses JavaFX, SQLite and JDBC. Maven supplies JavaFX dependencies.
- Docker is required for the isolated PostgreSQL integration tests.

## Configure Hootly Online

From the repository root in PowerShell, copy the example only if you do not already have `online/.env`:

    Copy-Item online/.env.example online/.env

Edit `online/.env` privately. Set `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `DATABASE_URL` to your PostgreSQL database; the Compose configuration initializes a new volume using `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`. Changing these variables later does not change credentials inside an existing PostgreSQL volume. Never delete an existing volume just to restart the app.

For account email codes, generate `OTP_HMAC_SECRET` as Base64 containing at least 32 cryptographically random bytes. Configure `EMAIL_PROVIDER=mailjet`, `MAILJET_API_KEY`, `MAILJET_SECRET_KEY`, and a verified `MAILJET_FROM_EMAIL`; `MAILJET_FROM_NAME` defaults to Hootly. Mailjet acceptance does not guarantee delivery. Keep these values out of Git. The configured SMTP transport is an optional alternative; do not configure both as simultaneous senders. Without email and OTP configuration, email verification cannot be delivered.

## Build and Run

Start PostgreSQL if necessary, preserving any existing database:

    docker compose --env-file online/.env -f online/docker-compose.yml up -d

In a separate terminal, start the backend. This script loads `online/.env` and then runs Spring Boot. Flyway applies pending migrations; the backend defaults to `127.0.0.1:8080`.

    powershell -NoProfile -File online/scripts/Start-Backend.ps1

In another terminal, start the frontend:

    cd online/frontend
    npm install
    npm run dev

Visit `http://localhost:5173`. The existing Vite proxy forwards `/api` and `/ws` to the backend. If Node is not on PowerShell's PATH and the optional portable installation exists, run `. ./online/scripts/Use-PortableNode.ps1` from the repository root before starting the frontend.

## Public Access

Use an HTTPS-capable public port-forwarding service and forward its public endpoint to the Vite frontend on port 5173. Keep PostgreSQL and the backend on loopback. Put the exact public HTTPS origin in `FRONTEND_URL` and `ALLOWED_ORIGINS` in `online/.env`; set `COOKIE_SECURE=true` for HTTPS. Before starting Vite, set `HOOTLY_PUBLIC_ORIGINS` to that exact origin, for example:

    $env:HOOTLY_PUBLIC_ORIGINS='https://your-public-host.example'
    cd online/frontend
    npm run dev

Restart the corresponding application process after changing its configuration. The forwarded origin must be trusted by the proxy configuration for correct HTTPS cookies, API requests, and WebSockets. Do not use a wildcard host allowance. Do not expose the backend or database directly.

## Tests

Tests use isolated databases and mock/capture email delivery; they do not require real test emails.

Desktop, isolated SQLite:

    mvn test

Online unit/HTTP tests and disposable PostgreSQL integration tests (Docker required):

    mvn -f online/backend/pom.xml verify

Frontend:

    cd online/frontend
    npm test
    npm run build

PostgreSQL integration tests use Testcontainers and require Docker plus the `postgres:17-alpine` image. Avoid `clean` or volume removal against running services.
