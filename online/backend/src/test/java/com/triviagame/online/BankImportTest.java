package com.triviagame.online;

import com.triviagame.api.OpenTdbClient;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BankImportTest extends HttpTestSupport {
    @MockitoBean OpenTdbClient api;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.triviagame.online.bank.QuestionBank bank;
    @Test void archivedQuestionsLeaveTheBankButKeepTheirStoredRecords() throws Exception {
        Browser user=registered(username());String category="Archived fixture "+UUID.randomUUID();
        var questions=List.of(
                Map.of("category",category,"text","Retained question","correctAnswer","True","incorrectAnswers",List.of("False"),"difficulty","easy","questionType","TRUE_FALSE"),
                Map.of("category",category,"text","Archived question","correctAnswer","True","incorrectAnswers",List.of("False"),"difficulty","easy","questionType","TRUE_FALSE"));
        assertThat(user.post("/bank/import",Map.of("source","archive-test","json",json.writeValueAsString(questions))).status()).isEqualTo(200);
        long id=jdbc.queryForObject("SELECT id FROM categories WHERE name=?",Long.class,category);
        jdbc.update("UPDATE questions SET archived=TRUE WHERE category_id=? AND text='Archived question'",id);
        assertThat(bank.categories()).filteredOn(c->c.id()==id).singleElement().satisfies(c->assertThat(c.questionCount()).isEqualTo(1));
        assertThat(bank.select(id,"any","any")).singleElement().satisfies(q->assertThat(q.question().text()).isEqualTo("Retained question"));
        jdbc.update("UPDATE questions SET archived=TRUE WHERE category_id=?",id);
        assertThat(bank.categories()).noneMatch(c->c.id()==id);
        assertThat(bank.select(null,"any","any")).noneMatch(q->q.question().category().equals(category));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE category_id=?",Integer.class,id)).isEqualTo(2);
    }
    @Test void importsAreDuplicateSafeAndMalformedBatchDoesNotChangeSavedData() throws Exception {
        Browser user=registered(username());String question="Unique "+UUID.randomUUID();
        String input=json.writeValueAsString(List.of(Map.of("category","Test import","text",question,"correctAnswer","Yes",
                "incorrectAnswers",List.of("No"),"difficulty","easy","questionType","MULTIPLE_CHOICE")));
        Map<String,String> request=Map.of("source","test.json","json",input);
        Result first=user.post("/bank/import",request);assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().get("inserted").asInt()).isEqualTo(1);
        assertThat(user.post("/bank/import",request).body().get("duplicates").asInt()).isEqualTo(1);
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class);
        assertThat(user.post("/bank/import",Map.of("source","bad.json","json","{broken")).status()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE text=?",Integer.class,question)).isEqualTo(1);
    }
    @Test void retiredFetchImportCannotAddAutomaticQuestionsToTheBank() throws Exception {
        Browser user=registered(username());int before=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class);
        assertThat(user.post("/bank/fetch",Map.of("amount",5,"difficulty","any","type","any")).status()).isEqualTo(410);
        verifyNoInteractions(api);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(before);
    }
    @Test void failedImportRollsBackEarlierInsertsInTheSameBatch() throws Exception {
        Browser user=registered(username());int before=jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class);
        String category="Rollback "+UUID.randomUUID();
        var first=Map.of("category",category,"text","Valid "+UUID.randomUUID(),"correctAnswer","Yes","incorrectAnswers",List.of("No"),"difficulty","easy","questionType","MULTIPLE_CHOICE");
        var second=Map.of("category",category,"text","Invalid "+UUID.randomUUID(),"correctAnswer","A".repeat(1001),"incorrectAnswers",List.of("No"),"difficulty","easy","questionType","MULTIPLE_CHOICE");
        Result result=user.post("/bank/import",Map.of("source","rollback.json","json",json.writeValueAsString(List.of(first,second))));
        assertThat(result.status()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questions",Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE name=?",Integer.class,category)).isZero();
    }
}
