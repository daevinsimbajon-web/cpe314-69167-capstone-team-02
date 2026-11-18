package com.triviagame.online.bank;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
@RequestMapping("/api/quizzes")
public class PersonalQuizController {
    private final PersonalQuizzes quizzes;
    private final QuestionImportService imports;

    public PersonalQuizController(PersonalQuizzes quizzes, QuestionImportService imports) {
        this.quizzes = quizzes;
        this.imports = imports;
    }

    public record QuestionImport(@NotBlank @Size(max = 500000) String json) {}

    @PostMapping("/import")
    List<PersonalQuizzes.Question> importQuestions(@Valid @RequestBody QuestionImport input) {
        return imports.draft(input.json());
    }

    @GetMapping
    List<PersonalQuizzes.Summary> list(Principal user) {
        return quizzes.list(id(user));
    }

    @GetMapping("/categories")
    List<PersonalQuizzes.Category> categories(Principal user) {
        return quizzes.categories(id(user));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    PersonalQuizzes.Category category(
            Principal user, @Valid @RequestBody PersonalQuizzes.CategoryName input) {
        return quizzes.category(id(user), input.name(), input.parentId());
    }

    @GetMapping("/library")
    List<PersonalQuizzes.LibraryQuestion> library(
            Principal user, @RequestParam(required = false) UUID categoryId) {
        return quizzes.library(id(user), categoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PersonalQuizzes.Detail create(Principal user, @Valid @RequestBody PersonalQuizzes.Save input) {
        return quizzes.create(id(user), input);
    }

    @GetMapping("/{quiz}")
    PersonalQuizzes.Detail get(Principal user, @PathVariable UUID quiz) {
        return quizzes.get(id(user), quiz);
    }

    @PutMapping("/{quiz}")
    PersonalQuizzes.Detail save(
            Principal user,
            @PathVariable UUID quiz,
            @Valid @RequestBody PersonalQuizzes.Save input) {
        return quizzes.save(id(user), quiz, input);
    }

    @PostMapping("/{quiz}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    PersonalQuizzes.Detail duplicate(Principal user, @PathVariable UUID quiz) {
        return quizzes.duplicate(id(user), quiz);
    }

    @DeleteMapping("/{quiz}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal user, @PathVariable UUID quiz) {
        quizzes.delete(id(user), quiz);
    }

    private static UUID id(Principal user) {
        return UUID.fromString(user.getName());
    }
}
