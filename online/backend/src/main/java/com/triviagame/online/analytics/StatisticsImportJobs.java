package com.triviagame.online.analytics;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Bounded background workers; durable staging and progress are scoped to the importing account. */
@Service
public class StatisticsImportJobs {
  private static final Logger LOG = LoggerFactory.getLogger(StatisticsImportJobs.class);
  private final JdbcTemplate jdbc;
  private final Comparisons comparisons;
  private final Clock clock;
  private final TransactionTemplate separate;
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(64),
          r -> {
            Thread t = new Thread(r, "stats-import");
            t.setDaemon(true);
            return t;
          });

  public record Job(UUID id, String status, int progress, String message) {}

  public StatisticsImportJobs(
      JdbcTemplate jdbc,
      Comparisons comparisons,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.comparisons = comparisons;
    this.clock = clock;
    separate = new TransactionTemplate(transactions);
    separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    // A stopped worker rolls its import transaction back. Let the user retry explicitly.
    jdbc.update(
        "UPDATE statistics_import_jobs SET status='FAILED',source_json=NULL,message='Import"
            + " interrupted by a server restart. Please import the file again.' WHERE status IN"
            + " ('QUEUED','PROCESSING')");
  }

  public Job submit(UUID owner, String source, String expected) {
    if (source.isBlank() || source.getBytes(StandardCharsets.UTF_8).length > 5 * 1024 * 1024)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a nonempty JSON file up to 5 MB.");
    if (expected != null && expected.length() > 120)
      throw new IllegalArgumentException("Invalid player identity.");
    UUID id = UUID.randomUUID();
    separate.executeWithoutResult(
        tx -> {
          jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", UUID.class, owner);
          if (jdbc.queryForObject(
                  "SELECT COUNT(*) FROM statistics_import_jobs WHERE owner_id=? AND status IN"
                      + " ('QUEUED','PROCESSING')",
                  Integer.class,
                  owner)
              >= 3)
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "Wait for your current imports to finish.");
          jdbc.update(
              "INSERT INTO"
                  + " statistics_import_jobs(id,owner_id,expected_player,source_json,status,created_at)"
                  + " VALUES(?,?,?,?,'QUEUED',?)",
              id,
              owner,
              expected,
              source,
              clock.instant().atOffset(ZoneOffset.UTC));
        });
    try {
      workers.execute(() -> run(id, owner, source, expected));
    } catch (RejectedExecutionException full) {
      finish(id, "FAILED", "The import queue is full. Please retry shortly.");
    }
    return get(owner, id);
  }

  private void run(UUID id, UUID owner, String source, String expected) {
    try {
      jdbc.update(
          "UPDATE statistics_import_jobs SET status='PROCESSING',progress=5 WHERE id=?", id);
      String result =
          comparisons.importHistory(
              owner,
              source,
              expected,
              progress ->
                  separate.executeWithoutResult(
                      tx ->
                          jdbc.update(
                              "UPDATE statistics_import_jobs SET progress=? WHERE id=?",
                              progress,
                              id)));
      finish(id, "COMPLETED", result);
    } catch (IllegalArgumentException invalid) {
      finish(id, "FAILED", invalid.getMessage());
    } catch (RuntimeException failure) {
      // Keep the export contents and database error details out of logs.
      LOG.warn("Statistics import {} failed with {}", id, failure.getClass().getSimpleName());
      finish(id, "FAILED", "The import could not be saved. Please retry.");
    }
  }

  private void finish(UUID id, String status, String message) {
    jdbc.update(
        "UPDATE statistics_import_jobs SET status=?,progress=100,message=?,source_json=NULL WHERE"
            + " id=?",
        status,
        message == null
            ? "Invalid export."
            : message.substring(0, Math.min(1000, message.length())),
        id);
  }

  public Job get(UUID owner, UUID id) {
    return jdbc
        .query(
            "SELECT id,status,progress,message FROM statistics_import_jobs WHERE owner_id=? AND"
                + " id=?",
            (rs, n) ->
                new Job(
                    rs.getObject("id", UUID.class),
                    rs.getString("status"),
                    rs.getInt("progress"),
                    rs.getString("message")),
            owner,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public List<Job> recent(UUID owner) {
    return jdbc.query(
        "SELECT id,status,progress,message FROM statistics_import_jobs WHERE owner_id=? ORDER BY"
            + " created_at DESC LIMIT 20",
        (rs, n) ->
            new Job(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getInt("progress"),
                rs.getString("message")),
        owner);
  }

  @PreDestroy
  public void close() {
    workers.shutdownNow();
  }
}
