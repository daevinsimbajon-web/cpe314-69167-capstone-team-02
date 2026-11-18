# Hootly final defense — presentation content

CPE 314 · Section 3A · Team 02 · 18 November 2026

This is a draft content outline, not the missing official `Final_Defense_Template` or a finished slide deck. Adapt slide order and time to the instructor's instructions when that template is available. Confirm the team member attributions and rehearse the actual demonstration before presenting. See [design document](design-document.md) for the diagrams and [known issues](KNOWN_ISSUES.md) for all 34 corrections and eight bugs.

## 1. Problem and solution

- Quiz hosts need a repeatable way to collect answers, calculate scores and retain results.
- Hootly supports online hosts and players with individual devices; the desktop application supports a local trivia night and preserves the required JavaFX/SQLite implementation.
- Show the purpose and intended users; do not promise external services will always be available.

## 2. Main features

- Accounts, verification codes, custom quizzes, OpenTDB questions, host rooms and room codes.
- Server-authoritative multiplayer countdowns, individual question timers and optional retry chances.
- Final standings, game history, personal results, score export/import and consent-based player comparison.
- Privacy: imported snapshots remain after disconnect; renewed live updates need fresh permission.

## 3. Architecture

Show the architecture diagram in design §3: React/Vite → Spring Boot controllers/services/repositories → PostgreSQL, plus OpenTDB and the configured mail transport. The JavaFX desktop app uses its own service/DAO layer and SQLite. Mention that desktop MVC separation is improved but not yet complete.

## 4. Database and ERDs

Show the SQLite and PostgreSQL diagrams in design §4. Explain players, sessions, questions and scores; the SQLite session/player join table; and online users, rooms, participants, answers and scores. SQLite scores refer to questions and derive category; stable IDs separate duplicate display names. PostgreSQL evolves through Flyway. Describe the migration's safety checks and explain that the diagrams are core overviews; the source schemas define all columns.

## 5. Object-oriented design

Show the class diagram: abstract `Question`, multiple-choice and true/false implementations, and category decorators (`ScienceQuestion`, `HistoryQuestion`, `EntertainmentQuestion`). Explain delegation of answer behavior and runtime dispatch for category scoring. Illustrate encapsulation with domain objects and stable IDs. Do not claim the online scoring engine uses category bonuses: online uses its own retry scoring rules.

## 6. Data structures and recursion

Show `GameEngine` using FIFO `ArrayDeque` for questions and a LIFO deque for scored-answer undo; show the reachable Undo control. Explain maps keyed by stable player IDs and recursive traversal in `Category`/`CategoryDao`. Use a three-level category example rather than claiming a fixed nesting depth.

## 7. API and JSON

Show the API sequence diagram in design §5. Walk through an OpenTDB request, DTO parsing/mapping and decoded question/answer text. Explain offline/provider failure and fewer-than-requested results. Show historical JSON import/export and how signed online identity, duplicate detection and conflict checks protect comparisons.

## 8. Concurrency and timing

Explain the app-owned desktop executor and JavaFX Task handlers, and show the synchronous importer scheduled in a background Task. For online rooms show the gameplay flow in design §6: server countdown, deadline, answer, optional five-second paused chance decision, final feedback, next question and standings. Explain that server submissions have a bounded transport grace and real network behavior still needs demonstration.

## 9. Analytics and visualization

Use actual persisted results. Show best-performing category and average session score or trend, with labeled bar and line charts. Explain that timeouts are finalized records and duplicate display names are distinguished by ID. On the online comparison screen explain imported history versus allowed live sharing.

## 10. Testing and limits

Actual audit runs, 18 November 2026:

| Test | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|
| Desktop Maven | 188 | 0 | 0 |
| Online backend Maven | 115 | 0 | 0 |
| Disposable PostgreSQL integration | 53 | 0 | 0 |
| Frontend Vitest | 137 | 0 | 0 |

Frontend production build succeeded. Test profiles capture or mock email; no real test email was sent. Real public forwarding, actual email delivery and the live defense demonstration still need team verification. Refer to the current test logs if another run changes these results.

## 11. Demonstration flow

1. Start the application using the README, with the existing database preserved. Confirm the frontend and backend are reachable.
2. Using two previously prepared accounts, show host room creation, joining, countdown, a correct answer and a wrong answer with a chance. Show the final leaderboard and history.
3. Show analytics and export/import a signed statistics file. Request sharing, allow it on the other account, disconnect, then request again and show that access remains off until a new Allow.
4. Open the JavaFX application. Show an actual category hierarchy, a scored-answer Undo, SQLite-backed history and labeled charts.
5. Explain what happens if OpenTDB or email is unavailable. Do not send a real email during the defense without the account owner's explicit permission.

## 12. Team and Q&A

The prior project document records these primary roles; team members must confirm their specific final work before presentation:

- John Daevin S. Simbajon — team lead and integration/DevOps.
- Clarence Philip A. Elegino — core object-oriented design and game logic.
- Justine B. Cuizon — database, data and API.
- Joshua Neil G. Malaya — UI/UX and JavaFX.
- Gebrielle P. Mirasol — analytics and QA.

Each member should explain a file and test they actually worked on. Be prepared to answer the review's questions: which desktop screen is final; why scores derive category through the question; why stable synchronization IDs exist; and what account/host data must persist. Confirm any personal-history claim with the member instead of inventing commit ownership.
