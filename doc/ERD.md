# Entity-Relationship Diagrams

## Reading the diagrams

PK identifies a primary key, FK identifies a foreign key, and UK identifies a unique key or unique index. When a key spans multiple columns, the key table lists them together. A UK marker on one part of a composite key does not imply single-column uniqueness. The diagrams mark columns that can be null. Dashed relationships are non-identifying, and solid relationships use the parent key as part of the child’s primary key. Some tables appear as reference boxes in more than one diagram.

## PostgreSQL for Hootly Online

The tables below were read from a disposable PostgreSQL 17 database after Flyway applied V1 through V16. Types such as `varchar(n)` and `timestamptz` are PostgreSQL aliases. Flyway maintains a separate schema-history table. The [SQL migrations](../online/backend/src/main/resources/db/migration/) and [Java migrations](../online/backend/src/main/java/db/migration/) define the full constraints and defaults.

### Accounts and email

Accounts use unique usernames and email addresses. Display names can repeat. Spring Session stores its attributes in a separate table with a composite PK and cascades their deletion when the session is deleted.

```mermaid
erDiagram
    users o|..o{ account_email_challenges : "user_id"
    account_email_challenges o|..o{ account_email_deliveries : "challenge_id"
    users o|..o{ account_email_deliveries : "user_id"
    users ||..o{ password_reset_grants : "user_id"
    spring_session ||--o{ spring_session_attributes : "session_primary_id"
    users {
        uuid id PK
        varchar(60) display_name
        varchar(24) username UK
        varchar(254) email UK
        varchar(100) password_hash
        timestamptz created_at
        timestamptz deleted_at "nullable"
        varchar(500) avatar_config "nullable"
        boolean email_verified
        boolean verification_required
        timestamptz email_verified_at "nullable"
    }
    account_email_challenges {
        uuid id PK
        uuid user_id FK "nullable"
        varchar(64) ticket_digest UK
        varchar(24) purpose
        varchar(254) email_address
        varchar(64) code_digest
        timestamptz expires_at
        timestamptz resend_at
        integer attempts
        timestamptz consumed_at "nullable"
        timestamptz created_at
        varchar(12) delivery_status
    }
    password_reset_grants {
        uuid id PK
        uuid user_id FK
        varchar(64) grant_digest UK
        varchar(24) purpose
        timestamptz expires_at
        timestamptz consumed_at "nullable"
    }
    account_email_deliveries {
        uuid id PK
        uuid user_id FK "nullable"
        uuid challenge_id FK "nullable"
        varchar(24) kind
        varchar(12) status
        timestamptz created_at
        timestamptz updated_at
        varchar(16) provider "nullable"
        varchar(40) provider_message_id "nullable"
        varchar(32) failure_category "nullable"
    }
    spring_session {
        char(36) primary_id PK
        char(36) session_id UK
        bigint creation_time
        bigint last_access_time
        integer max_inactive_interval
        bigint expiry_time
        varchar(100) principal_name "nullable"
    }
    spring_session_attributes {
        char(36) session_primary_id PK,FK
        varchar(200) attribute_name PK
        bytea attribute_bytes
    }
```

| Table | PK | UK, including unique indexes | FK columns and targets |
|---|---|---|---|
| `users` | `(id)` | `(email)`<br>`(username)` | — |
| `account_email_challenges` | `(id)` | `(ticket_digest)` | `(user_id) → users(id)` |
| `password_reset_grants` | `(id)` | `(grant_digest)` | `(user_id) → users(id)` |
| `account_email_deliveries` | `(id)` | — | `(challenge_id) → account_email_challenges(id)`<br>`(user_id) → users(id)` |
| `spring_session` | `(primary_id)` | `(session_id)` | — |
| `spring_session_attributes` | `(session_primary_id, attribute_name)` | — | `(session_primary_id) → spring_session(primary_id) ON DELETE CASCADE` |

### Quizzes and question sources

A quiz collection orders its questions through `quiz_collection_questions`. A question can be shared by multiple collections belonging to its owner. Personal categories support nesting. Their unique key is `(owner_user_id, normalized_name)`. It rejects repeated names within one branch and across separate branches owned by the same account. Different owners can use the same name. The OpenTDB category cache holds category metadata. The old shared-bank import table retains its records.

```mermaid
erDiagram
    categories o|..o{ categories : "parent_id"
    users ||..o{ personal_quiz_categories : "owner_user_id"
    personal_quiz_categories o|..o{ personal_quiz_categories : "parent_id"
    users o|..o{ question_bank_imports : "user_id"
    categories ||..o{ questions : "category_id"
    users o|..o{ questions : "owner_user_id"
    questions ||..o{ quiz_collection_questions : "question_id"
    quiz_collections ||--o{ quiz_collection_questions : "quiz_id"
    personal_quiz_categories o|..o{ quiz_collections : "custom_category_id"
    users ||..o{ quiz_collections : "owner_user_id"
    users ||..o{ quiz_practice_sessions : "owner_id"
    quiz_collections ||..o{ quiz_practice_sessions : "quiz_id"
    categories {
        bigint id PK "identity"
        varchar(200) name
        varchar(200) normalized_name UK
        bigint parent_id FK "nullable"
    }
    personal_quiz_categories {
        uuid id PK
        uuid owner_user_id FK,UK "UK (owner_user_id, normalized_name)"
        varchar(100) name
        varchar(100) normalized_name UK "UK (owner_user_id, normalized_name)"
        timestamptz created_at
        uuid parent_id FK "nullable"
    }
    questions {
        uuid id PK
        bigint category_id FK
        text text
        text correct_answer
        text incorrect_answers
        varchar(10) difficulty
        varchar(20) question_type
        varchar(80) external_id UK
        varchar(64) content_key UK
        timestamptz created_at
        boolean archived
        uuid owner_user_id FK "nullable"
    }
    quiz_collections {
        uuid id PK
        uuid owner_user_id FK
        varchar(120) title
        varchar(2000) description
        uuid custom_category_id FK "nullable"
        varchar(10) visibility
        varchar(10) status
        integer version
        text draft_document
        timestamptz created_at
        timestamptz updated_at
        timestamptz deleted_at "nullable"
    }
    quiz_collection_questions {
        uuid quiz_id PK,FK
        uuid question_id FK
        integer display_order PK
    }
    quiz_practice_sessions {
        uuid id PK
        uuid owner_id FK
        uuid quiz_id FK
        text state_document
        integer version
        uuid last_action_id "nullable"
        timestamptz expires_at
    }
    opentdb_category_cache {
        integer id PK
        text document
        timestamptz fetched_at
    }
    question_bank_imports {
        uuid id PK
        uuid user_id FK "nullable"
        varchar(100) source
        integer received_count
        integer inserted_count
        timestamptz imported_at
    }
    import_mutex {
        integer id PK
    }
```

| Table | PK | UK, including unique indexes | FK columns and targets |
|---|---|---|---|
| `categories` | `(id)` | `(normalized_name)` | `(parent_id) → categories(id)` |
| `personal_quiz_categories` | `(id)` | `(owner_user_id, normalized_name)` | `(owner_user_id) → users(id)`<br>`(parent_id) → personal_quiz_categories(id)` |
| `questions` | `(id)` | `(content_key)`<br>`(external_id)` | `(category_id) → categories(id)`<br>`(owner_user_id) → users(id)` |
| `quiz_collections` | `(id)` | — | `(custom_category_id) → personal_quiz_categories(id)`<br>`(owner_user_id) → users(id)` |
| `quiz_collection_questions` | `(quiz_id, display_order)` | — | `(question_id) → questions(id)`<br>`(quiz_id) → quiz_collections(id)` |
| `quiz_practice_sessions` | `(id)` | — | `(owner_id) → users(id)`<br>`(quiz_id) → quiz_collections(id)` |
| `opentdb_category_cache` | `(id)` | — | — |
| `question_bank_imports` | `(id)` | — | `(user_id) → users(id)` |
| `import_mutex` | `(id)` | — | — |

### Multiplayer and scoring

A room can have at most one game session because `game_sessions.room_id` is unique. The participant and score tables join users to rooms and sessions. Online scores are totals per user and session. Individual answers link to `room_questions`. Instant Quiz snapshots can have a null source question and store their category name. Saved-quiz snapshots link to source questions. `room_retry_decisions` references the compound attempt identity `(user_id, submission_id)`. Before starting an older room, the service verifies that its stored snapshot positions cover zero through the declared question count minus one. The UK on `(room_id, ordinal)` prevents repeated positions.

```mermaid
erDiagram
    room_questions ||..o{ answer_attempts : "room_question_id"
    users ||..o{ answer_attempts : "user_id"
    room_questions ||..o{ answers : "room_question_id"
    users ||..o{ answers : "user_id"
    quiz_rooms ||..o| game_sessions : "room_id"
    categories o|..o{ quiz_rooms : "category_id"
    quiz_collections o|..o{ quiz_rooms : "custom_quiz_id"
    users ||..o{ quiz_rooms : "host_id"
    quiz_rooms ||--o{ room_participants : "room_id"
    users ||--o{ room_participants : "user_id"
    quiz_rooms ||--o{ room_player_progress : "room_id"
    users ||--o{ room_player_progress : "user_id"
    room_questions ||--o{ room_question_presentations : "room_question_id"
    users ||--o{ room_question_presentations : "user_id"
    questions o|..o{ room_questions : "question_id"
    quiz_rooms ||..o{ room_questions : "room_id"
    users ||--o{ room_retry_decisions : "user_id"
    answer_attempts ||--o| room_retry_decisions : "user_id, submission_id"
    game_sessions ||--o{ scores : "session_id"
    users ||--o{ scores : "user_id"
    quiz_rooms {
        uuid id PK
        varchar(8) code UK
        uuid host_id FK
        varchar(80) title
        varchar(16) status
        bigint category_id FK "nullable"
        varchar(10) difficulty
        varchar(20) question_type
        integer question_count
        integer seconds_per_question
        integer max_players
        varchar(100) password_hash "nullable"
        integer current_question
        timestamptz deadline "nullable"
        timestamptz created_at
        timestamptz expires_at
        integer retry_allowance
        varchar(30) scoring_version
        timestamptz question_started_at "nullable"
        varchar(12) question_source
        integer opentdb_category_id "nullable"
        uuid custom_quiz_id FK "nullable"
        integer custom_quiz_version "nullable"
        boolean independent_flow
        boolean reveal_answers
        timestamptz starts_at "nullable"
    }
    room_participants {
        uuid room_id PK,FK
        uuid user_id PK,FK
        varchar(8) role
        timestamptz joined_at
        timestamptz left_at "nullable"
        boolean removed_by_host
    }
    room_questions {
        uuid id PK
        uuid room_id FK,UK "UK (room_id, ordinal)"
        uuid question_id FK "nullable"
        integer ordinal UK "UK (room_id, ordinal)"
        text text
        text correct_answer
        text choices
        varchar(10) difficulty
        varchar(20) question_type
        integer points
        timestamptz presented_at "nullable"
        varchar(200) category_name "nullable"
    }
    game_sessions {
        uuid id PK
        uuid room_id FK,UK
        timestamptz started_at
        timestamptz finished_at "nullable"
    }
    answers {
        uuid id PK
        uuid room_question_id FK,UK "UK (room_question_id, user_id)"
        uuid user_id FK,UK "UK (room_question_id, user_id)"
        integer selected_index "nullable"
        boolean is_correct
        integer points
        timestamptz answered_at
        boolean timed_out
        boolean first_try_correct "nullable"
        integer attempt_count "nullable"
        integer retries_used "nullable"
        bigint elapsed_ms "nullable"
    }
    answer_attempts {
        uuid id PK
        uuid room_question_id FK,UK "UK (room_question_id, user_id, attempt_number)"
        uuid user_id FK,UK "UK (room_question_id, user_id, attempt_number), UK (user_id, submission_id)"
        uuid submission_id UK "UK (user_id, submission_id)"
        integer attempt_number UK "UK (room_question_id, user_id, attempt_number)"
        integer selected_index
        boolean is_correct
        varchar(12) outcome
        integer retries_remaining
        timestamptz accepted_at
    }
    scores {
        uuid session_id PK,FK
        uuid user_id PK,FK
        integer points
        integer correct_count
        integer retries_remaining
        boolean forfeited
    }
    room_player_progress {
        uuid room_id PK,FK
        uuid user_id PK,FK
        integer ordinal
        timestamptz question_started_at
        timestamptz deadline
        timestamptz feedback_until "nullable"
        timestamptz completed_at "nullable"
    }
    room_retry_decisions {
        uuid user_id PK,FK
        uuid submission_id PK,FK
        boolean use_chance
        timestamptz decided_at
    }
    room_question_presentations {
        uuid room_question_id PK,FK
        uuid user_id PK,FK
        timestamptz presented_at
    }
```

| Table | PK | UK, including unique indexes | FK columns and targets |
|---|---|---|---|
| `quiz_rooms` | `(id)` | `(code)` | `(category_id) → categories(id)`<br>`(custom_quiz_id) → quiz_collections(id)`<br>`(host_id) → users(id)` |
| `room_participants` | `(room_id, user_id)` | — | `(room_id) → quiz_rooms(id)`<br>`(user_id) → users(id)` |
| `room_questions` | `(id)` | `(room_id, ordinal)` | `(question_id) → questions(id)`<br>`(room_id) → quiz_rooms(id)` |
| `game_sessions` | `(id)` | `(room_id)` | `(room_id) → quiz_rooms(id)` |
| `answers` | `(id)` | `(room_question_id, user_id)` | `(room_question_id) → room_questions(id)`<br>`(user_id) → users(id)` |
| `answer_attempts` | `(id)` | `(room_question_id, user_id, attempt_number)`<br>`(user_id, submission_id)` | `(room_question_id) → room_questions(id)`<br>`(user_id) → users(id)` |
| `scores` | `(session_id, user_id)` | — | `(session_id) → game_sessions(id)`<br>`(user_id) → users(id)` |
| `room_player_progress` | `(room_id, user_id)` | — | `(room_id) → quiz_rooms(id)`<br>`(user_id) → users(id)` |
| `room_retry_decisions` | `(user_id, submission_id)` | — | `(user_id) → users(id)`<br>`(user_id, submission_id) → answer_attempts(user_id, submission_id)` |
| `room_question_presentations` | `(room_question_id, user_id)` | — | `(room_question_id) → room_questions(id)`<br>`(user_id) → users(id)` |

### Statistics and sharing

Compared games use a composite key of comparison member and exported game ID. The exported ID has no FK to the local game session table. A pending sharing request, approval, or revocation belongs to a unique requester-recipient pair.

```mermaid
erDiagram
    compared_players ||--o{ compared_games : "member_id"
    users ||..o{ compared_players : "owner_id"
    users o|..o{ compared_players : "verified_user_id"
    users ||..o{ statistics_import_jobs : "owner_id"
    users ||..o{ statistics_sharing : "recipient_id"
    users ||..o{ statistics_sharing : "requester_id"
    analytics_signing_keys {
        integer id PK
        varchar(100) secret
    }
    compared_players {
        uuid id PK
        uuid owner_id FK,UK "UK (owner_id, export_player_id)"
        varchar(120) export_player_id UK "UK (owner_id, export_player_id)"
        uuid verified_user_id FK "nullable"
        varchar(100) label
        text player_json
        timestamptz exported_at
        timestamptz last_synced_at "nullable"
    }
    compared_games {
        uuid member_id PK,FK
        varchar(120) game_id PK
        text game_json
    }
    statistics_sharing {
        uuid id PK
        uuid requester_id FK,UK "UK (requester_id, recipient_id)"
        uuid recipient_id FK,UK "UK (requester_id, recipient_id)"
        varchar(10) status
        timestamptz requested_at
        timestamptz updated_at
        timestamptz granted_at "nullable"
        timestamptz revoked_at "nullable"
    }
    statistics_import_jobs {
        uuid id PK
        uuid owner_id FK
        varchar(120) expected_player "nullable"
        text source_json "nullable"
        varchar(12) status
        integer progress
        varchar(1000) message "nullable"
        timestamptz created_at
    }
```

| Table | PK | UK, including unique indexes | FK columns and targets |
|---|---|---|---|
| `analytics_signing_keys` | `(id)` | — | — |
| `compared_players` | `(id)` | `(owner_id, export_player_id)` | `(owner_id) → users(id)`<br>`(verified_user_id) → users(id)` |
| `compared_games` | `(member_id, game_id)` | — | `(member_id) → compared_players(id) ON DELETE CASCADE` |
| `statistics_sharing` | `(id)` | `(requester_id, recipient_id)` | `(recipient_id) → users(id)`<br>`(requester_id) → users(id)` |
| `statistics_import_jobs` | `(id)` | — | `(owner_id) → users(id)` |

## SQLite for the JavaFX desktop application

The eight tables below were read from a disposable SQLite file initialized by the desktop application’s schema and DAOs. SQLite stores the timestamps shown here as text and the timed-out and correct flags as integers.

```mermaid
erDiagram
    categories o|..o{ categories : "parent_id"
    categories ||..o{ questions : "category_id"
    questions ||..o{ scores : "question_id"
    players ||..o{ scores : "player_id"
    game_sessions ||..o{ scores : "session_id"
    players ||--o{ session_players : "player_id"
    game_sessions ||--o{ session_players : "session_id"
    questions ||--o{ timed_answers : "question_id"
    players ||--o{ timed_answers : "player_id"
    game_sessions ||--o{ timed_answers : "session_id"
    categories {
        integer id PK "rowid"
        text name
        integer parent_id FK "nullable"
    }
    game_sessions {
        integer id PK "rowid"
        text played_at
        text session_sync_id UK "nullable"
    }
    players {
        integer id PK "rowid"
        text name
        text player_sync_id UK "nullable"
    }
    question_bank_imports {
        integer id PK "rowid"
        text source
        integer inserted_count
        text imported_at
    }
    questions {
        integer id PK "rowid"
        text text
        text correct_answer
        text incorrect_answers
        text difficulty
        text question_type
        text created_at "nullable"
        text external_id UK "nullable"
        integer category_id FK
    }
    scores {
        integer id PK "rowid"
        integer session_id FK
        integer player_id FK
        integer question_id FK
        integer is_correct
        integer points
        text score_sync_id UK "nullable"
    }
    session_players {
        integer session_id PK,FK,UK "UK (session_id, turn_order)"
        integer player_id PK,FK
        integer turn_order UK "UK (session_id, turn_order)"
        integer seconds_per_question
    }
    timed_answers {
        integer session_id PK,FK
        integer player_id PK,FK
        integer question_id PK,FK
        integer elapsed_ms
        integer timed_out
    }
```

| Table | PK | UK, including unique indexes | FK columns and targets |
|---|---|---|---|
| `categories` | `(id)` | — | `(parent_id) → categories(id)` |
| `game_sessions` | `(id)` | `(session_sync_id)` | — |
| `players` | `(id)` | `(player_sync_id)` | — |
| `question_bank_imports` | `(id)` | — | — |
| `questions` | `(id)` | `(external_id)` | `(category_id) → categories(id)` |
| `scores` | `(id)` | `(score_sync_id)` | `(question_id) → questions(id)`<br>`(player_id) → players(id)`<br>`(session_id) → game_sessions(id)` |
| `session_players` | `(session_id, player_id)` | `(session_id, turn_order)` | `(player_id) → players(id)`<br>`(session_id) → game_sessions(id)` |
| `timed_answers` | `(session_id, player_id, question_id)` | — | `(question_id) → questions(id)`<br>`(player_id) → players(id)`<br>`(session_id) → game_sessions(id)` |

The `session_players` table joins sessions and players. A desktop score belongs to exactly one question through a required `question_id` FK. Its category comes from that question. The desktop `timed_answers` table has its own composite PK of session, player, and question. There is no direct FK from a timed answer to a score.

Desktop categories use insert and update triggers to stop duplicate sibling names. This rule is distinct from a declared UK. The nullable external question ID and the sync IDs use unique indexes. The schema keeps nullable fields for compatibility with older SQLite files.

Schema sources: [Database.java](../src/main/java/com/triviagame/dao/Database.java), [SessionPlayerDao.java](../src/main/java/com/triviagame/dao/SessionPlayerDao.java), [TimedAnswerDao.java](../src/main/java/com/triviagame/dao/TimedAnswerDao.java), and [QuestionBankService.java](../src/main/java/com/triviagame/service/QuestionBankService.java).

## Unique index definitions

The UK columns above also include these explicit unique indexes.

- `CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id)`
- `CREATE UNIQUE INDEX ux_game_sessions_session_sync_id ON game_sessions(session_sync_id) `
- `CREATE UNIQUE INDEX ux_players_player_sync_id ON players(player_sync_id)`
- `CREATE UNIQUE INDEX ux_questions_external_id ON questions(external_id) `
- `CREATE UNIQUE INDEX ux_scores_score_sync_id ON scores(score_sync_id) `
