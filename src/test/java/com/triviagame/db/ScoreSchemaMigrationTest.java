package com.triviagame.db;

import java.nio.file.Path;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScoreSchemaMigrationTest {
    @TempDir Path directory;
    private Connection legacy(String name) throws Exception {
        Connection db=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve(name));
        try(Statement s=db.createStatement()){
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE categories(id INTEGER PRIMARY KEY, name TEXT NOT NULL, parent_id INTEGER REFERENCES categories(id))");
            s.execute("CREATE TABLE players(id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
            s.execute("CREATE TABLE game_sessions(id INTEGER PRIMARY KEY, played_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            s.execute("CREATE TABLE questions(id INTEGER PRIMARY KEY AUTOINCREMENT,text TEXT NOT NULL,correct_answer TEXT NOT NULL,incorrect_answers TEXT NOT NULL,difficulty TEXT NOT NULL,question_type TEXT NOT NULL DEFAULT 'MULTIPLE_CHOICE',created_at TEXT,external_id TEXT,category_id INTEGER NOT NULL REFERENCES categories(id))");
            s.execute("CREATE TABLE scores(id INTEGER PRIMARY KEY,session_id INTEGER NOT NULL REFERENCES game_sessions(id),player_id INTEGER NOT NULL REFERENCES players(id),category_id INTEGER NOT NULL REFERENCES categories(id),question_id INTEGER NOT NULL REFERENCES questions(id),is_correct INTEGER NOT NULL,points INTEGER NOT NULL)");
            s.execute("INSERT INTO categories VALUES(1,'Science',NULL),(2,'History',NULL),(3,'Old duplicate',1),(4,'Old duplicate',1)");
            s.execute("INSERT INTO players VALUES(1,'Same'),(2,'Same')");
            s.execute("INSERT INTO game_sessions(id) VALUES(1)");
            s.execute("INSERT INTO questions VALUES(1,'Question?','Yes','[\"No\"]','easy','TRUE_FALSE','2020-01-01',NULL,1)");
            s.execute("INSERT INTO scores VALUES(11,1,1,1,1,1,10),(12,1,2,2,1,0,0)");
        }
        return db;
    }
    @Test void migrationPreservesScoresAndLegacyCategoryMeaning() throws Exception {
        try(Connection db=legacy("migration.db")){
            Database.initializeSchema(db);Database.initializeSchema(db);
            try(Statement s=db.createStatement()){
                try(ResultSet rows=s.executeQuery("SELECT s.id,s.player_id,s.points,q.category_id,s.score_sync_id FROM scores s JOIN questions q ON q.id=s.question_id ORDER BY s.id")){
                    assertTrue(rows.next());assertEquals(11,rows.getInt(1));assertEquals(1,rows.getInt(4));assertNotNull(rows.getString(5));
                    assertTrue(rows.next());assertEquals(12,rows.getInt(1));assertEquals(2,rows.getInt(2));assertEquals(2,rows.getInt(4));assertNotNull(rows.getString(5));assertFalse(rows.next());
                }
                try(ResultSet columns=s.executeQuery("PRAGMA table_info(scores)")){while(columns.next())assertNotEquals("category_id",columns.getString("name"));}
                try(ResultSet broken=s.executeQuery("PRAGMA foreign_key_check")){assertFalse(broken.next());}
                try(ResultSet ids=s.executeQuery("SELECT COUNT(DISTINCT player_sync_id) FROM players")){assertTrue(ids.next());assertEquals(2,ids.getInt(1));}
                assertThrows(SQLException.class,()->s.execute("INSERT INTO categories(name,parent_id) VALUES('OLD DUPLICATE',1)"));
                assertEquals(1,s.executeUpdate("INSERT INTO categories(name,parent_id) VALUES('Old duplicate',2)"));
            }
        }
    }
    @Test void unknownLegacyColumnsAbortWithoutDeletingData() throws Exception {
        try(Connection db=legacy("custom.db");Statement s=db.createStatement()){
            s.execute("ALTER TABLE scores ADD COLUMN custom_note TEXT");s.execute("UPDATE scores SET custom_note='keep me' WHERE id=11");
            assertThrows(SQLException.class,()->Database.initializeSchema(db));
            try(ResultSet result=s.executeQuery("SELECT category_id,custom_note FROM scores WHERE id=11")){assertTrue(result.next());assertEquals(1,result.getInt(1));assertEquals("keep me",result.getString(2));}
        }
    }
    @Test void dependentScoreReferencesAbortWithoutCascadingDeletes() throws Exception {
        try(Connection db=legacy("dependent.db");Statement s=db.createStatement()){
            s.execute("CREATE TABLE saved_note(id INTEGER PRIMARY KEY,score_id INTEGER REFERENCES scores(id) ON DELETE CASCADE)");
            s.execute("INSERT INTO saved_note VALUES(1,11)");
            assertThrows(SQLException.class,()->Database.initializeSchema(db));
            try(ResultSet rows=s.executeQuery("SELECT COUNT(*) FROM saved_note WHERE score_id=11")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
            try(ResultSet rows=s.executeQuery("SELECT COUNT(*) FROM scores")){assertTrue(rows.next());assertEquals(2,rows.getInt(1));}
        }
    }
    @Test void conflictingScoreWithTimedAnswerAbortsWithoutBreakingTheirLink() throws Exception {
        try(Connection db=legacy("timed-conflict.db");Statement s=db.createStatement()){
            s.execute("CREATE TABLE timed_answers(session_id INTEGER,player_id INTEGER,question_id INTEGER,elapsed_ms INTEGER,timed_out INTEGER,PRIMARY KEY(session_id,player_id,question_id))");
            s.execute("INSERT INTO timed_answers VALUES(1,2,1,1234,0)");
            SQLException error=assertThrows(SQLException.class,()->Database.initializeSchema(db));
            assertTrue(error.getMessage().contains("timed answers"));
            try(ResultSet rows=s.executeQuery("SELECT s.category_id,s.question_id,t.elapsed_ms FROM scores s JOIN timed_answers t ON t.session_id=s.session_id AND t.player_id=s.player_id AND t.question_id=s.question_id WHERE s.id=12")){
                assertTrue(rows.next());assertEquals(2,rows.getInt(1));assertEquals(1,rows.getInt(2));assertEquals(1234,rows.getInt(3));
            }
        }
    }
}
