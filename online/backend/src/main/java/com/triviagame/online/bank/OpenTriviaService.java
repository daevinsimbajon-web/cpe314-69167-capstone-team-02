package com.triviagame.online.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triviagame.api.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.*;
import java.util.*;

@Service
public class OpenTriviaService {
    public record Catalog(
            List<OpenTdbClient.Category> categories,
            boolean cached,
            Instant fetchedAt,
            String source) {}

    private final OpenTdbClient client;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final long intervalMillis;
    private Instant nextQuestionRequest = Instant.MIN;

    public OpenTriviaService(
            OpenTdbClient client,
            JdbcTemplate jdbc,
            ObjectMapper json,
            Clock clock,
            @Value("${app.opentdb.interval-ms:5000}") long intervalMillis) {
        this.client = client;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.intervalMillis = intervalMillis;
    }

    public synchronized Catalog categories() {
        Catalog saved = null;
        var rows =
                jdbc.queryForList(
                        "SELECT document,fetched_at FROM opentdb_category_cache WHERE id=1");
        if (!rows.isEmpty())
            try {
                saved =
                        new Catalog(
                                OpenTdbClient.parseCategories((String) rows.get(0).get("document")),
                                true,
                                com.triviagame.online.DatabaseTimes.instant(
                                        rows.get(0).get("fetched_at")),
                                "OpenTDB official category cache");
            } catch (IOException ignored) {
                /* An invalid cache is never offered as official data. */
            }
        if (saved != null && saved.fetchedAt().plus(Duration.ofHours(24)).isAfter(clock.instant()))
            return saved;
        try {
            var categories = client.fetchCategories();
            var at = clock.instant();
            String document = json.writeValueAsString(Map.of("trivia_categories", categories));
            int updated =
                    jdbc.update(
                            "UPDATE opentdb_category_cache SET document=?,fetched_at=? WHERE id=1",
                            document,
                            OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
            if (updated == 0)
                jdbc.update(
                        "INSERT INTO opentdb_category_cache(id,document,fetched_at) VALUES(1,?,?)",
                        document,
                        OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
            return new Catalog(categories, false, at, "OpenTDB official category API");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (saved != null) return saved;
            throw unavailable();
        } catch (IOException failure) {
            if (saved != null) return saved;
            throw unavailable();
        }
    }

    public List<BankQuestion> fetch(int amount, Integer category, String difficulty, String type) {
        if (amount < 1
                || amount > 50
                || difficulty == null
                || !Set.of("any", "easy", "medium", "hard").contains(difficulty)
                || type == null
                || !Set.of("any", "multiple", "boolean").contains(type))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose valid Instant Quiz settings");
        var catalog = categories();
        if (category != null && catalog.categories().stream().noneMatch(c -> c.id() == category))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose an available category");
        synchronized (this) {
            if (clock.instant().isBefore(nextQuestionRequest))
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait five seconds before preparing another quiz.");
            nextQuestionRequest = clock.instant().plusMillis(intervalMillis);
        }
        try {
            var distinct=new LinkedHashMap<String,BankQuestion>();
            int requested=amount;
            OpenTdbResult last=null;
            for(int attempt=0;attempt<3&&distinct.size()<amount;attempt++){
                if(attempt>0){
                    // One centralized reservation also covers internal retries. Other hosts get
                    // a friendly 429 while this bounded preparation owns the provider slot.
                    long wait;
                    synchronized(this){wait=Math.max(0,Duration.between(clock.instant(),nextQuestionRequest).toMillis());nextQuestionRequest=clock.instant().plusMillis(wait+intervalMillis);}
                    if(wait>0)Thread.sleep(wait);
                }
                last=client.fetchResult(requested,category,difficulty,type);
                if(last.status()!=OpenTdbStatus.OK){
                    Integer response=last.responseCode();
                    if(Integer.valueOf(1).equals(response)&&requested>1){requested=Math.max(1,requested/2);continue;}
                    if(Integer.valueOf(5).equals(response))continue;
                    throw questionFailure(last);
                }
                for(var q:last.questions())distinct.putIfAbsent(q.category()+"\n"+q.text(),q);
                requested=Math.min(50,amount-distinct.size());
            }
            if(distinct.size()!=amount){
                if(distinct.isEmpty()&&last!=null&&last.status()!=OpenTdbStatus.OK)throw questionFailure(last);
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Only "+distinct.size()+" distinct questions were available with those settings. Choose a smaller count or broader filters. Your room has not been created.");
            }
            var questions=List.copyOf(distinct.values());
            String categoryName =
                    category == null
                            ? null
                            : catalog.categories().stream()
                                    .filter(c -> c.id() == category)
                                    .findFirst()
                                    .orElseThrow()
                                    .name();
            for (var q : questions) {
                if (categoryName != null && !categoryName.equals(q.category())
                        || !difficulty.equals("any") && !difficulty.equals(q.difficulty())
                        || !type.equals("any") && !modelType(type).equals(q.questionType()))
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Those questions did not match your settings. Please try again.");
            }
            return questions;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private ResponseStatusException questionFailure(OpenTdbResult result) {
        int code = result.responseCode() == null ? -1 : result.responseCode();
        String message =
                switch (code) {
                    case 1 ->
                            "No questions found for those settings. Try another category or"
                                + " difficulty, or fewer questions.";
                    case 2 ->
                            "We could not prepare questions with those settings. Try another"
                                + " selection.";
                    case 3 -> "Your question request expired. Please try again.";
                    case 4 -> "No more questions match this selection. Try another category.";
                    case 5 -> "Questions are busy right now. Wait five seconds and try again.";
                    default -> result.message();
                };
        return new ResponseStatusException(
                code == 5 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY, message);
    }

    public static String modelType(String type) {
        return switch (type) {
            case "multiple" -> "MULTIPLE_CHOICE";
            case "boolean" -> "TRUE_FALSE";
            default -> type;
        };
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Categories are unavailable right now. Please try again shortly, or choose My"
                    + " Quizzes.");
    }
}
