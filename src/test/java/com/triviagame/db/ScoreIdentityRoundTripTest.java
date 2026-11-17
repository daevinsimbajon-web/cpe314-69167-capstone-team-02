package com.triviagame.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScoreIdentityRoundTripTest {
    @TempDir Path directory;
    @Test void sameNamePlayersRemainSeparateInExportAndImport() throws Exception {
        String original=System.getProperty("trivia.database.path");
        try{
            System.setProperty("trivia.database.path",directory.resolve("source.db").toString());Database.initializeSchema();
            var players=new PlayerDao();int first=players.addPlayer("Alex"),second=players.addPlayer("Alex");
            int category=new CategoryDao().addCategory("Science");int question=new QuestionDao().addQuestion("A star?","Sun","[\"Moon\"]","easy",category);
            var scores=new ScoreDao();int session=scores.createGameSession();scores.addScore(session,first,category,question,true,10);scores.addScore(session,second,category,question,false,2);
            Path export=directory.resolve("scores.json");assertEquals(2,new ScoreJsonExporter().exportFile(export));
            var json=new ObjectMapper();ArrayNode exported=(ArrayNode)json.readTree(Files.readString(export));
            assertNotEquals(exported.get(0).get("playerSyncId").asText(),exported.get(1).get("playerSyncId").asText());
            System.setProperty("trivia.database.path",directory.resolve("destination.db").toString());Database.initializeSchema();
            try(var importer=new ScoreJsonImporter()){
                var result=importer.importFile(export);assertEquals(2,result.imported());assertTrue(result.errors().isEmpty(),result.errors().toString());
                var totals=new AnalyticsDao().getTopPlayers(10);assertEquals(2,totals.size());assertNotEquals(totals.get(0).playerId(),totals.get(1).playerId());assertEquals(10,totals.get(0).totalPoints());assertEquals(2,totals.get(1).totalPoints());
                Path legacy=directory.resolve("legacy.json");Files.writeString(legacy,"[{\"player\":\"Alex\",\"category\":\"Science\",\"questionText\":\"A star?\",\"isCorrect\":true,\"points\":10}]");
                var ambiguous=importer.importFile(legacy);assertEquals(0,ambiguous.imported());assertTrue(ambiguous.errors().get(0).contains("ambiguous player name"));
            }
        }finally{if(original==null)System.clearProperty("trivia.database.path");else System.setProperty("trivia.database.path",original);}
    }
}
