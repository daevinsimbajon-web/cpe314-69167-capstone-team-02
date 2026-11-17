package com.triviagame.online.bank;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triviagame.api.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** PostgreSQL adapter for the existing immutable BankQuestion and parser. No SQLite access. */
@Service
public class QuestionBank {
    public record ImportReport(int received,int inserted,int duplicates) { }
    public record Category(long id,String name,int questionCount) { }
    public record Entry(UUID id,BankQuestion question) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    public QuestionBank(JdbcTemplate jdbc,ObjectMapper json,Clock clock) { this.jdbc=jdbc;this.json=json;this.clock=clock; }
    @Bean OpenTdbClient openTdbClient(@Value("${app.opentdb.endpoint:https://opentdb.com/api.php}") String endpoint,@Value("${app.opentdb.timeout-ms:12000}") long timeout) { return new OpenTdbClient(java.net.URI.create(endpoint),Duration.ofMillis(timeout)); }
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.seed-example-bank",havingValue="true",matchIfMissing=true)
    ApplicationRunner starterBank(QuestionBank bank) {
        return args -> {
            try(var input=new ClassPathResource("example_question_bank.json").getInputStream()) {
                bank.importQuestions(new QuestionParser().parse(new String(input.readAllBytes(),StandardCharsets.UTF_8)),"Capstone examples",null);
            }
        };
    }
    public List<Category> categories() {
        return jdbc.query("""
                SELECT c.id,c.name,COUNT(q.id) AS question_count FROM categories c
                JOIN questions q ON q.category_id=c.id AND q.archived=FALSE AND q.owner_user_id IS NULL GROUP BY c.id,c.name ORDER BY c.name
                """,(rs,n)->new Category(rs.getLong("id"),rs.getString("name"),rs.getInt("question_count")));
    }
    @Transactional
    public ImportReport importQuestions(List<BankQuestion> input,String source,UUID user) {
        if(input==null||input.isEmpty()||input.size()>1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Import 1–1000 questions at a time");
        jdbc.queryForObject("SELECT id FROM import_mutex WHERE id=1 FOR UPDATE",Integer.class);
        int inserted=0;
        for(BankQuestion q:input) {
            String category=normalize(q.category());
            String contentKey=hash(part(category)+part(normalize(q.text())));
            String identity=q.externalId()==null?"text:"+part(category)+part(normalize(q.text())):"id:"+part(q.externalId());
            String externalId="sha256:"+hash(part(normalize(source))+identity);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE content_key=? OR external_id=?",Integer.class,contentKey,externalId)>0) continue;
            List<Long> categories=jdbc.query("SELECT id FROM categories WHERE normalized_name=?",(rs,n)->rs.getLong(1),category);
            if(categories.isEmpty()) jdbc.update("INSERT INTO categories(name,normalized_name) VALUES(?,?)",q.category(),category);
            Long categoryId=jdbc.queryForObject("SELECT id FROM categories WHERE normalized_name=?",Long.class,category);
            // Keep database and UI bounds explicit; any failure rolls back the entire batch.
            if(q.category().length()>200||q.text().length()>4000||q.correctAnswer().length()>1000||q.incorrectAnswers().size()>10
                    ||q.incorrectAnswers().stream().anyMatch(a->a.length()>1000)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Question or answer exceeds the supported size");
            jdbc.update("""
                    INSERT INTO questions(id,category_id,text,correct_answer,incorrect_answers,difficulty,question_type,external_id,content_key)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,UUID.randomUUID(),categoryId,q.text(),q.correctAnswer(),write(q.incorrectAnswers()),q.difficulty(),q.questionType(),externalId,contentKey);
            inserted++;
        }
        jdbc.update("INSERT INTO question_bank_imports(id,user_id,source,received_count,inserted_count,imported_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(),user,source,input.size(),inserted,OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
        return new ImportReport(input.size(),inserted,input.size()-inserted);
    }
    public List<Entry> select(Long category,String difficulty,String type) {
        return jdbc.query("""
                SELECT q.*,c.name AS category_name FROM questions q JOIN categories c ON c.id=q.category_id
                WHERE q.archived=FALSE AND q.owner_user_id IS NULL AND (CAST(? AS BIGINT) IS NULL OR q.category_id=?) AND (?='any' OR q.difficulty=?) AND (?='any' OR q.question_type=?)
                """,this::entry,category,category,difficulty,difficulty,type,type);
    }
    private Entry entry(ResultSet rs,int row) throws SQLException {
        return new Entry(rs.getObject("id",UUID.class),new BankQuestion(rs.getString("category_name"),rs.getString("text"),
                rs.getString("correct_answer"),read(rs.getString("incorrect_answers")),rs.getString("difficulty"),rs.getString("question_type")));
    }
    public String write(List<String> values) {
        try{return json.writeValueAsString(values);}catch(IOException ex){throw new IllegalStateException("Cannot serialize question",ex);}
    }
    public List<String> read(String value) {
        try{return json.readValue(value,new TypeReference<List<String>>(){});}catch(IOException ex){throw new IllegalStateException("Saved question is invalid",ex);}
    }
    private static String normalize(String value){return value.strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    private static String part(String value){return value.length()+":"+value;}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
