CPE 314 — Software Design 

University of Cebu 

Computer Engineering Department 

# **Trivia & Quiz Night Manager** 

_Project Design Document_ 

|**Group Name/ No.:**|2|
|---|---|
|**Members:**|John Daevin S. Simbajon -(Team Lead + Integrator/DevsOps)|
||Clarence Philip A. Elegino - (Core OOP/Logic Developer)|
||Justine B. Cuizon - (Database Admin + Data Developer + API)|
||Joshua Neil G. Malaya - (UI/UX Designer + JavaFX Developer)|
||Gebrielle P. Mirasol - (Analytics + QA)|
|**Instructor:**|Richyll Son|
|**Term:**|First Semester,S.Y. 2026-2027|
|**Document Version / Date:**|_V0.1 - 10/26/26_|



## **Table of Contents** 

|_Trivia & Quiz Night Manager ............................................................................................................................ 1_|
|---|
|_Table of Contents .......................................................................................................................................... 2_|
|_Revision History ............................................................................................................................................ 3_|
|_Formatting Guidelines ....................................................................................._**_Error! Bookmark not defined._**|
|_1. Introduction & Project Overview ............................................................................................................... 3_|
|_2. Preliminary ER Sketch & Team Roles ......................................................................................................... 4_|
|_3. System Architecture & Object-Oriented Design ......................................................................................... 5_|
|_4. Data Model & Database Design ................................................................................................................ 5_|
|_5. API Integration Design .............................................................................................................................. 5_|
|_6. Concurrency Design .................................................................................................................................. 5_|
|_7. Data Mining & Visualization Design .......................................................................................................... 5_|
|_8. Testing Summary ....................................................................................................................................... 6_|
|_9. Individual Contributions ............................................................................................................................ 6_|
|_Appendix ....................................................................................................................................................... 6_|




## **Revision History** 

_Update this table each week as your team adds or revises sections of this document._ 

|**Version**|**Date**|**Authors**|**Changes**|
|---|---|---|---|
|0.1|10/24/26-<br>10/26/26|Justine B. Cuizon,<br>Clarence A. Elegino,<br>Joshua G. Malaya,<br>Gebrielle P. Mirasol,<br>John S. Simbajon|Initial draft — Week 8 sections<br>(Section 1 to 2)<br>Removing Unnecesary Instruction<br>Generating the Table of Contents|
|0.2|||Week 9 sections added|
|0.3|||Week 10 sections added|
|1.0|||Final compilation and polish —<br>Week 11|



## **1. Introduction & Project Overview** 

- Trivia & Quiz Game Night Manager is a Java desktop application that helps friends, classmates, and community groups organize and run trivia nights. Players enter their names, choose a category, and answer questions fetched live from the Open Trivia Database, with an “undo last answer” option for mistakes. Every player, game session, and score is saved in a local SQLite database, and past score history can be imported from CSV files. The app then analyzes the saved results and shows average score per category, most-missed questions, and score trends over time as labeled bar and line charts. Theme: Custom idea (an entertainment and social-gaming tool for trivia nights). 

Problem solved: Quiz hosts usually keep score on paper or in scattered spreadsheets, so results are easy to lose and hard to compare from one night to the next. 

Key value proposition: One app that fetches questions, scores each player, stores the history, and turns it into clear charts, so groups can see who is improving and which categories are hardest. 


## **2. Preliminary ER Sketch & Team Roles** 

**Figure 1: Entities, Key Attributes and Relationships** 



<!-- Start of picture text -->
CATEGORY<br>nt “ PK<br>stig name uk<br>nt parents FK<br>QUESTION<br>re “ PK<br>sting text J GAME_SESSION PLAYER<br>sting commect_answer int 4 mK mo PK<br>sting incorrect_answers, dotetme<br>playedat strmg name UK<br>nt<br>——_categoryia FK<br>SCORE<br>int Py PK<br>1 int sessions FK<br>int players PK<br>int catogoryid FK<br>int questions FK<br>boolean s_correct<br><!-- End of picture text -->

**Table 1: Team members and primary roles** 

|**Member**|**Primary Role**|
|---|---|
|_John Daevin S. Simbajon_|Team Lead + Integrator/DevOps|
|_Gebrielle P. Mirasol_|Core OOP/Logic Developer|
|_Justine B. Cuizon_|Database Admin + Data Developer + API|
|_Clarence Philip A. Elegino_|UI/UX Designer + JavaFX Developer|
|_Joshua Malaya_|Analytics + QA|


