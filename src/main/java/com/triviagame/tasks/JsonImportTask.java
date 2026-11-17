package com.triviagame.tasks;

import com.triviagame.api.QuestionParser;
import com.triviagame.db.QuestionBankService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.concurrent.Task;

public final class JsonImportTask
        extends Task<QuestionBankService.ImportReport> {

    private final Path file;

    public JsonImportTask(Path file) {
        this.file = file;
    }

    @Override
    protected QuestionBankService.ImportReport call()
            throws Exception {

        updateMessage("Reading JSON question file...");

        var questions = new QuestionParser().parse(
            Files.readString(file, StandardCharsets.UTF_8)
        );

        if (isCancelled()) {
            return null;
        }

        updateMessage("Saving imported questions...");

        return new QuestionBankService().importQuestions(
            questions,
            "JSON:" + file.getFileName()
        );
    }
}