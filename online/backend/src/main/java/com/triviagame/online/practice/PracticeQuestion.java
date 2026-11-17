package com.triviagame.online.practice;

import java.util.List;

/** A private quiz snapshot, independent of HTTP and persistence. */
public record PracticeQuestion(String text, String type, List<String> choices, int correctIndex) {
    public PracticeQuestion {
        choices = List.copyOf(choices);
        if (correctIndex < 0 || correctIndex >= choices.size()) {
            throw new IllegalArgumentException("The question needs a correct answer.");
        }
    }
}
