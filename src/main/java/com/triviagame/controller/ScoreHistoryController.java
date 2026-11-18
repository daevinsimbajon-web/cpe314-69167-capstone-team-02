package com.triviagame.controller;

import com.triviagame.dao.ScoreJsonExporter;
import com.triviagame.dao.ScoreJsonImporter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/** Coordinates desktop score import and export from a worker thread. */
public final class ScoreHistoryController {
  /** Result shown after importing a desktop scoreboard. */
  public record ImportSummary(int imported, int skipped, List<String> errors, int duplicates) {}

  private final ScoreJsonImporter importer = new ScoreJsonImporter();
  private final ScoreJsonExporter exporter = new ScoreJsonExporter();

  public ImportSummary importFrom(Path file) {
    var result = importer.importFile(file);
    return new ImportSummary(
        result.imported(), result.skipped(), result.errors(), result.duplicates());
  }

  public int exportTo(Path file) throws IOException, SQLException {
    return exporter.exportFile(file);
  }
}
