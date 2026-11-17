# CPE314-Group2-S-Final-Project
## **Project Information**

**Project Title**: Trivia & Quiz Game Night Manager

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


## Requirements

- Java Development Kit (JDK) 17
- Apache Maven
- JavaFX dependencies managed by Maven

## Build and Run

Compile the application:

    mvn compile

Run the automated tests:

    mvn test

Launch the JavaFX application:

    mvn javafx:run

Main entry point:

    com.triviagame.App

## SQLite Database

The application uses SQLite through JDBC.

Database file:

    data/trivia.db

The data directory is created automatically when needed.

The five main database tables are:

- categories
- players
- game_sessions
- questions
- scores

## Offline Question Bank

Trivia & Quiz Night Manager is designed for offline, local multiplayer use.

Preloaded questions are available locally. Additional questions can be downloaded from OpenTDB and saved in SQLite.

## OpenTDB Limitations

- Request amount: 1 to 50 questions
- Multiple choice: 1 correct and 3 incorrect answers
- Total multiple-choice options: 4
- True/False options: 2
- Custom number of API answer choices: Not supported

The current API client does not send category or difficulty filter parameters.

The primary JavaFX application and separate M2 multiplayer demo are not yet fully integrated.
