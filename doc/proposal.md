

#  **CPE 314 — Software Design & Software Design Laboratory** 

### **Week 8 — Project Proposal** 

_<mark>Due end of Week 8 session | 10% of the Finals Term Grade | Scored out of 100</mark>_ 

|**Group Name / No.:**|2|
|---|---|
|**Members:**|Justine Cuizon, Clarence Philip Elegino, Joshua Malaya, Gebrielle P. Mirasol, John Daevin S. Simbajon|
|**Repository URL:**|https://github.com/daevinsimbajon-web/cpe314-69167-capstone-team-02.git|
|**Theme:**|Trivia & Quiz Night Manager|
|**API You Will Use:**|Open Trivia Database — https://opentdb.com/api_config.php — no key required; example: GET opentdb.com/api.php?amount=10&category=9&difficulty=easy&type=multiple|



## **1. One-Paragraph Pitch** 

Trivia & Quiz Night Manager is an offline-first Java desktop application designed for local multiplayer trivia games. Players can register their names, choose a category, and take turns answering questions using the same device. The game uses a Queue to manage question order and a Stack to support undo functionality. SQLite stores questions, players, game sessions, and scores locally. OpenTDB is an optional source for downloading additional trivia questions, which can be saved for offline use. The system also supports score tracking, analytics, and visualizations to help participants review their performance across games.



## **2. Entity-Relationship Sketch** 

## **3. Feature Checklist**
Planned Feature Requirement(s) It Satisfies** Question hierarchy (abstract Question with OOP and recursion Science, History, and Entertainment subclasses) and recursive category-tree walk Game engine: Queue serves questions in order, Queue and Stack Stack provides “undo last answer” SQLite database layer: Player, GameSession, Database and ER model Question, Category, and Score tables with DAO classes using PreparedStatement Open Trivia Database API client with JSON parsed REST API into Question objects Data mining and charts: average score per Data mining, visualization and tests on category, most-missed questions, score trend, AnalyticsService) shown as bar and line charts JavaFX GUI (buttons, category ComboBox, playerGUI with + wired controls , concurrency name TextField, ListView/TableView) with JSON import and API fetch running on background ~~—~~ threads and a progress bar 


## **4. Task Split** 

| **Member** | **Primary Responsibility (this milestone)** 
|John Daevin S. Simbajon (Team Lead + Integrator/DevOps)| Project and package structure, schema.sql, .gitignore, shared Eclipse build path (User Library), GitHub repository and branch rules, reviewing and merging all pull requests, and the MainApp entry point with the JavaFX run configuration (VM arguments). Integrates every member’s package into one working app and tracks the weekly milestones|
|-|-|
|Clarence Philip A. Elegino (Core OOP/Logic Developer)|trivia.model package: abstract Question class,ScienceQuestion, HistoryQuestion, EntertainmentQuestion (each with its own scoring rule), plus Player and Category. trivia.game package: GameEngine (Queue serves questions in order, Stack for “undo last answer”) and CategoryTree(recursive categorywalk).|
|Justine B. Cuizon (Database Admin + Data Developer + API)|trivia.db package: Database connection class and the DAO classes (PlayerDao, QuestionDao, CategoryDao, ScoreDao) using PreparedStatement. trivia.api package: OpenTdbClient and QuestionParser for the Open Trivia Database. FetchQuestionsTask and CsvImportTask (background threads withprogress reporting)|
|Joshua Neil G. Malaya (UI/UX Designer + JavaFX Developer)|trivia.ui package: the Setup, Game, History, and Analytics views, with buttons, category ComboBox, player-name TextField, and ListView/TableView wired to event handlers. Layout and styling, progress bar and status labels, error alerts, and the wiringof the background tasks to the screens|
|Gebrielle P. Mirasol (Analytics + QA)|trivia.analytics package: AnalyticsService mining<br>methods (average score per category, most-missed questions, score trend) and LoadAnalyticsTask. AnalyticsCharts (labeled bar and line charts). Owns QA: the AnalyticsServiceTest suite, test plan, edge-case checks, and a final run-through of all 9 requirements before each milestone and the defense|



## **5. Public API You Plan to Use** 

**Open Trivia Database (OpenTDB), https://opentdb.com/api.php** 

It is a free public REST API that returns multiple-choice and true/false trivia questions as JSON, with the category, difficulty, correct answer, and incorrect answers for each question. The app fetches these questions and parses them into Question objects (Science, History, or Entertainment) for the game. 



## 6. Risks / Open Questions

### Risk 1: What if OpenTDB becomes unavailable?

OpenTDB may become temporarily unavailable because of network problems, API rate limits, or server errors. This could prevent the application from downloading additional questions.

Mitigation:
The system will handle network and API errors and notify the user. Previously downloaded questions will remain available in SQLite, allowing the game to continue offline.

### Risk 2: How will the game support offline multiplayer?

Trivia & Quiz Night Manager is designed for offline, local multiplayer gameplay. Players participate using the same device and take turns answering trivia questions.

Mitigation:
The system will store player records, questions, game sessions, and scores locally using SQLite. Preloaded and previously downloaded questions will be available without an internet connection.

### Risk 3: How will duplicate questions be prevented?

Repeated API downloads or JSON imports may contain questions already stored in the database.

Mitigation:
QuestionBankService will normalize category names and question text, compare imported questions against existing records, and skip duplicates.

### Risk 4: What happens if the SQLite database cannot be accessed?

Database failures, missing directories, or file permission errors may prevent the application from retrieving or saving data.

Mitigation:
The system will automatically create the required database directory, handle SQL exceptions, and display meaningful error messages.

### Risk 5: Can the number of multiple-choice answers be customized?

The standard OpenTDB multiple-choice format provides one correct answer and three incorrect answers, giving four choices per question.

OpenTDB does not provide a parameter for requesting a different number of answer choices.

Mitigation:
The application will use the choices returned by OpenTDB. Custom questions imported through JSON may support a different number of answer choices.

The number of questions downloaded can be configured separately from the number of answer choices.
