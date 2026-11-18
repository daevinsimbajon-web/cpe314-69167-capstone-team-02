package com.triviagame.online.bank;

import com.triviagame.api.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/bank")
public class BankController {
    public record JsonImport(
            @NotBlank @Size(max = 80) String source, @NotBlank @Size(max = 500000) String json) {}

    public record Fetch(
            @Min(1) @Max(50) int amount,
            @Pattern(regexp = "any|easy|medium|hard") String difficulty,
            @Pattern(regexp = "any|multiple|boolean") String type) {}

    private final QuestionBank bank;
    private final OpenTriviaService trivia;
    private final QuestionImportService imports;

    public BankController(
            QuestionBank bank, OpenTriviaService trivia, QuestionImportService imports) {
        this.bank = bank;
        this.trivia = trivia;
        this.imports = imports;
    }

    @GetMapping("/opentdb/categories")
    OpenTriviaService.Catalog officialCategories() {
        return trivia.categories();
    }

    @GetMapping("/categories")
    List<QuestionBank.Category> categories() {
        return bank.categories();
    }

    @PostMapping("/import")
    QuestionBank.ImportReport importJson(@Valid @RequestBody JsonImport input, Principal user) {
        return imports.shared(input.source(), input.json(), UUID.fromString(user.getName()));
    }

    @PostMapping("/fetch")
    QuestionBank.ImportReport fetch(@Valid @RequestBody Fetch input, Principal user) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Instant Quiz prepares questions when you create a room. Choose Instant Quiz in"
                    + " room setup.");
    }
}
