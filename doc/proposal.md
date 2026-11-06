

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

Trivia & Quiz Game Night Manager is a Java desktop app that helps friends, classmates, and community groups run organized trivia nights. Players enter their names, pick a category, and answer questions fetched live from the Open Trivia Database, with each question served in order and an “undo last answer” option for mistakes. The app saves every player, game session, and score in a local SQLite database, and it can import past score history from CSV files without freezing the interface. It then analyzes the data to show average score per category, most-missed questions, and score trends over time as labeled bar and line charts. It is built for casual quiz hosts and groups who want to track results across game nights instead of keeping score on paper. 


## **2. Entity-Relationship Sketch** 

## **3. Feature Checklist**
| Planned Feature | Requirement Satisfied |
| :--- | :--- |
| Abstract Question, MultipleChoiceQuestion, and TrueFalseQuestion | OOP, inheritance, and polymorphism |
| Recursive Category hierarchy | Recursion |
| GameEngine using Queue for question ordering and Stack for Undo | Queue and Stack |
| SQLite database with DAO classes using PreparedStatement | JDBC and database integration |
| OpenTdbClient and QuestionParser | Public REST API and JSON parsing |
| QuestionBankService with local question storage | Offline-first gameplay |
| JavaFX application with event handlers | GUI |
| FetchQuestionsTask, JsonImportTask, and ScoreJsonImporter | Concurrency |
| AnalyticsDao and AnalyticsScreen | Data mining and visualization |
| JUnit 5 automated tests | Automated Testing Evidence |


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

## **6. Risks / Open Questions** 



