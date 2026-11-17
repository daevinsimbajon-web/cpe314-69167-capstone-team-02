package com.triviagame.api;

import java.util.List;

/** Provider failures are data, with no JSON tree or transport exception escaping to the service. */
public record OpenTdbResult(
        OpenTdbStatus status, List<BankQuestion> questions, Integer responseCode, String message) {
    public OpenTdbResult {
        questions = List.copyOf(questions);
    }
}
