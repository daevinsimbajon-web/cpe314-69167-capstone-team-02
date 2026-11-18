package com.triviagame.db;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
/**
 * SQLite connection and additive schema migrations.
 * Existing scores, game sessions, players and questions are preserved.
 */
public final class Database {
 private Database() {
 }
 public static Connection getConnection() throws SQLException {
 Path databasePath = Path.of(System.getProperty("trivia.database.path", "data/trivia.db"));
 try {
 Files.createDirectories(databasePath.toAbsolutePath().getParent());
 } catch (IOException exception) {
 throw new SQLException("Cannot create database directory", exception);
 }
 Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
 try (Statement statement = connection.createStatement()) {
 statement.execute("PRAGMA foreign_keys = ON");
 statement.execute("PRAGMA busy_timeout = 5000");
 } catch (SQLException exception) {
 connection.close();
 throw exception;
 }
 return connection;
 }
 public static void initializeSchema() throws SQLException {
 try (Connection connection = getConnection()) {
 initializeSchema(connection);
 }
 }
 // Also allows migration checks against isolated databases.
 static void initializeSchema(Connection connection) throws SQLException {
 try (Statement statement = connection.createStatement()) {
 statement.execute("BEGIN IMMEDIATE");
 try {
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS categories (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL,
 parent_id INTEGER,
 FOREIGN KEY (parent_id) REFERENCES categories(id)
 )
 """);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS players (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 name TEXT NOT NULL
 )
 """);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS game_sessions (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
 )
 """);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS questions (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 text TEXT NOT NULL,
 correct_answer TEXT NOT NULL,
 incorrect_answers TEXT NOT NULL,
 difficulty TEXT NOT NULL,
 question_type TEXT NOT NULL DEFAULT 'MULTIPLE_CHOICE',
 created_at TEXT DEFAULT CURRENT_TIMESTAMP,
 external_id TEXT,
 category_id INTEGER NOT NULL,
 FOREIGN KEY (category_id) REFERENCES categories(id)
 )
 """);
 if (!hasColumn(connection, "questions", "question_type")) {
 statement.executeUpdate("""
 ALTER TABLE questions
 ADD COLUMN question_type TEXT NOT NULL
 DEFAULT 'MULTIPLE_CHOICE'
 """);
 }
 migrateQuestions(connection);
 statement.executeUpdate("""
 CREATE TABLE IF NOT EXISTS scores (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 session_id INTEGER NOT NULL,
 player_id INTEGER NOT NULL,
 question_id INTEGER NOT NULL,
 is_correct INTEGER NOT NULL CHECK (is_correct IN (0, 1)),
 points INTEGER NOT NULL,
 FOREIGN KEY (session_id) REFERENCES game_sessions(id),
 FOREIGN KEY (player_id) REFERENCES players(id),
 FOREIGN KEY (question_id) REFERENCES questions(id)
 )
 """);
 // Stable IDs are generated once and retained through re-export.
 // These columns are optional for old code, which keeps working.
 if (!hasColumn(connection, "game_sessions", "session_sync_id")) {
 statement.executeUpdate(
 "ALTER TABLE game_sessions ADD COLUMN session_sync_id TEXT");
 }
 if (!hasColumn(connection, "players", "player_sync_id")) {
 statement.executeUpdate("ALTER TABLE players ADD COLUMN player_sync_id TEXT");
 }
 if (!hasColumn(connection, "scores", "score_sync_id")) {
 statement.executeUpdate(
 "ALTER TABLE scores ADD COLUMN score_sync_id TEXT");
 }
 migrateScores(connection);
 statement.executeUpdate("UPDATE players SET player_sync_id=lower(hex(randomblob(16))) WHERE player_sync_id IS NULL OR player_sync_id=''");
 statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_players_player_sync_id ON players(player_sync_id)");
 // Drop the earlier trigger if present: it changes SQLite's reported insert count.
 statement.executeUpdate("DROP TRIGGER IF EXISTS trg_player_sync_id");
 // Backfill any old records. Also repairs rows inserted by old
 // code before a previous version of this migration ran.
 statement.executeUpdate("""
 UPDATE game_sessions
 SET session_sync_id = lower(hex(randomblob(16)))
 WHERE session_sync_id IS NULL OR session_sync_id = ''
 """);
 statement.executeUpdate("""
 UPDATE scores
 SET score_sync_id = lower(hex(randomblob(16)))
 WHERE score_sync_id IS NULL OR score_sync_id = ''
 """);
 statement.executeUpdate("""
 CREATE UNIQUE INDEX IF NOT EXISTS
 ux_game_sessions_session_sync_id
 ON game_sessions(session_sync_id)
 """);
 statement.executeUpdate("""
 CREATE UNIQUE INDEX IF NOT EXISTS
 ux_scores_score_sync_id
 ON scores(score_sync_id)
 """);
 // New gameplay inserts through the unchanged ScoreDao still
 // receive permanent IDs automatically.
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_session_sync_id
 AFTER INSERT ON game_sessions
 WHEN NEW.session_sync_id IS NULL OR NEW.session_sync_id = ''
 BEGIN
 UPDATE game_sessions
 SET session_sync_id = lower(hex(randomblob(16)))
 WHERE id = NEW.id;
 END
 """);
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_score_sync_id
 AFTER INSERT ON scores
 WHEN NEW.score_sync_id IS NULL OR NEW.score_sync_id = ''
 BEGIN
 UPDATE scores
 SET score_sync_id = lower(hex(randomblob(16)))
 WHERE id = NEW.id;
 END
 """);
 // Prevent new duplicate sibling names without deleting or renaming legacy rows.
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_category_sibling_insert
 BEFORE INSERT ON categories
 WHEN EXISTS (SELECT 1 FROM categories WHERE parent_id IS NEW.parent_id
     AND lower(trim(name))=lower(trim(NEW.name)))
 BEGIN SELECT RAISE(ABORT, 'Category already exists under this parent'); END
 """);
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_category_sibling_update
 BEFORE UPDATE OF name,parent_id ON categories
 WHEN EXISTS (SELECT 1 FROM categories WHERE id<>NEW.id AND parent_id IS NEW.parent_id
     AND lower(trim(name))=lower(trim(NEW.name)))
 BEGIN SELECT RAISE(ABORT, 'Category already exists under this parent'); END
 """);
 statement.execute("COMMIT");
 } catch (SQLException | RuntimeException exception) {
 try {
 statement.execute("ROLLBACK");
 } catch (SQLException rollbackFailure) {
 exception.addSuppressed(rollbackFailure);
 }
 throw exception;
 }
 }
 }
 /** Removes the redundant score category while preserving legacy conflicting categories. */
 private static void migrateScores(Connection connection) throws SQLException {
 if (!hasColumn(connection,"scores","category_id")) return;
 try (Statement statement=connection.createStatement()) {
 try (ResultSet columns=statement.executeQuery("PRAGMA table_info(scores)")) {
 java.util.Set<String> known=java.util.Set.of("id","session_id","player_id","category_id","question_id","is_correct","points","score_sync_id");
 while(columns.next()) if(!known.contains(columns.getString("name")))
 throw new SQLException("Cannot migrate scores with unrecognized columns; existing data was left unchanged");
 }
 try (ResultSet extras=statement.executeQuery("SELECT name FROM sqlite_master WHERE tbl_name='scores' AND type IN ('index','trigger') AND sql IS NOT NULL AND name NOT IN ('ux_scores_score_sync_id','trg_score_sync_id')")) {
 if(extras.next())throw new SQLException("Cannot migrate scores with custom indexes or triggers; existing data was left unchanged");
 }
 java.util.List<String> tables=new java.util.ArrayList<>();
 try(ResultSet names=statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'scores'")){
 while(names.next())tables.add(names.getString(1));
 }
 for(String table:tables){
 try(ResultSet keys=statement.executeQuery("PRAGMA foreign_key_list('"+table.replace("'","''")+"')")){
 while(keys.next())if("scores".equalsIgnoreCase(keys.getString("table")))
 throw new SQLException("Cannot migrate scores referenced by another table; existing data was left unchanged");
 }
 }
 try (ResultSet invalid=statement.executeQuery("SELECT COUNT(*) FROM scores s LEFT JOIN questions q ON q.id=s.question_id LEFT JOIN categories c ON c.id=s.category_id WHERE q.id IS NULL OR c.id IS NULL")) {
 if(invalid.next()&&invalid.getInt(1)>0)throw new SQLException("Cannot migrate scores with missing questions or categories; existing data was left unchanged");
 }
 java.util.List<int[]> conflicts=new java.util.ArrayList<>();
 try(ResultSet rows=statement.executeQuery("SELECT DISTINCT s.question_id,s.category_id FROM scores s JOIN questions q ON q.id=s.question_id WHERE s.category_id<>q.category_id")){
 while(rows.next())conflicts.add(new int[]{rows.getInt(1),rows.getInt(2)});
 }
 // A timed answer uses the original question ID as part of its identity. If a
 // conflicting score is linked to one, cloning only the question would break
 // that relationship. Refuse the migration instead of guessing or losing data.
 if(!conflicts.isEmpty()&&tables.stream().anyMatch("timed_answers"::equalsIgnoreCase)){
 try(ResultSet rows=statement.executeQuery("""
 SELECT COUNT(*) FROM timed_answers t WHERE EXISTS (
 SELECT 1 FROM scores s JOIN questions q ON q.id=s.question_id
 WHERE s.session_id=t.session_id AND s.player_id=t.player_id
 AND s.question_id=t.question_id AND s.category_id<>q.category_id)
 """)){
 if(rows.next()&&rows.getInt(1)>0)throw new SQLException("Cannot migrate conflicting score categories linked to timed answers; existing data was left unchanged");
 }
 }
 for(int[] conflict:conflicts){
 try(var copy=connection.prepareStatement("INSERT INTO questions(text,correct_answer,incorrect_answers,difficulty,question_type,created_at,external_id,category_id) SELECT text,correct_answer,incorrect_answers,difficulty,question_type,created_at,NULL,? FROM questions WHERE id=?")){
 copy.setInt(1,conflict[1]);copy.setInt(2,conflict[0]);if(copy.executeUpdate()!=1)throw new SQLException("Cannot preserve historical question category");
 }
 long clone;
 try(ResultSet row=statement.executeQuery("SELECT last_insert_rowid()")){if(!row.next())throw new SQLException("Cannot identify historical question copy");clone=row.getLong(1);}
 try(var update=connection.prepareStatement("UPDATE scores SET question_id=? WHERE question_id=? AND category_id=?")){
 update.setLong(1,clone);update.setInt(2,conflict[0]);update.setInt(3,conflict[1]);update.executeUpdate();
 }
 }
 statement.executeUpdate("""
 CREATE TABLE scores_migrated (
 id INTEGER PRIMARY KEY AUTOINCREMENT, session_id INTEGER NOT NULL,
 player_id INTEGER NOT NULL, question_id INTEGER NOT NULL,
 is_correct INTEGER NOT NULL CHECK (is_correct IN (0,1)), points INTEGER NOT NULL,
 score_sync_id TEXT,
 FOREIGN KEY (session_id) REFERENCES game_sessions(id),
 FOREIGN KEY (player_id) REFERENCES players(id),
 FOREIGN KEY (question_id) REFERENCES questions(id))
 """);
 statement.executeUpdate("INSERT INTO scores_migrated(id,session_id,player_id,question_id,is_correct,points,score_sync_id) SELECT id,session_id,player_id,question_id,is_correct,points,score_sync_id FROM scores");
 statement.executeUpdate("DROP TABLE scores");
 statement.executeUpdate("ALTER TABLE scores_migrated RENAME TO scores");
 }
 }
 private static void migrateQuestions(Connection connection) throws SQLException {
 try (Statement statement = connection.createStatement()) {
 // SQLite cannot ADD COLUMN with a CURRENT_TIMESTAMP default on populated tables.
 if (!hasColumn(connection, "questions", "created_at")) {
 statement.executeUpdate("ALTER TABLE questions ADD COLUMN created_at TEXT");
 }
 if (!hasColumn(connection, "questions", "external_id")) {
 statement.executeUpdate("ALTER TABLE questions ADD COLUMN external_id TEXT");
 }
 statement.executeUpdate("""
 UPDATE questions SET created_at = CURRENT_TIMESTAMP
 WHERE created_at IS NULL OR trim(created_at) = ''
 """);
 // Preserve every legacy row and relationship. Only the oldest row retains a
 // conflicting import ID; other copies remain available with an unknown ID.
 statement.executeUpdate("""
 UPDATE questions SET external_id = NULL
 WHERE external_id = '' OR id NOT IN (
 SELECT MIN(id) FROM questions WHERE external_id IS NOT NULL GROUP BY external_id
 ) AND external_id IS NOT NULL
 """);
 statement.executeUpdate("""
 CREATE UNIQUE INDEX IF NOT EXISTS ux_questions_external_id ON questions(external_id)
 """);
 statement.executeUpdate("""
 CREATE TRIGGER IF NOT EXISTS trg_question_created_at
 AFTER INSERT ON questions
 WHEN NEW.created_at IS NULL OR trim(NEW.created_at) = ''
 BEGIN
 UPDATE questions SET created_at = CURRENT_TIMESTAMP WHERE id = NEW.id;
 END
 """);
 }
 }
 private static boolean hasColumn(
 Connection connection, String table, String column)
 throws SQLException {
 // Both names are constant literals controlled by this class.
 try (Statement statement = connection.createStatement();
 ResultSet result = statement.executeQuery(
 "PRAGMA table_info(" + table + ")")) {
 while (result.next()) {
 if (column.equalsIgnoreCase(result.getString("name"))) {
 return true;
 }
 }
 }
 return false;
 }
}
