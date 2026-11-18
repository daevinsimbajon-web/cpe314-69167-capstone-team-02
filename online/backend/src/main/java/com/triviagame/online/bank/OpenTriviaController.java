package com.triviagame.online.bank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenTriviaController {
    private final OpenTriviaService trivia;

    public OpenTriviaController(OpenTriviaService trivia) {
        this.trivia = trivia;
    }

    @GetMapping("/api/bank/opentdb/categories")
    OpenTriviaService.Catalog officialCategories() {
        return trivia.categories();
    }
}
