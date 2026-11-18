package com.triviagame.online.practice;

import java.util.List;

/** Ordered state that can reconstruct both the queue and the history stack after restart. */
public record PracticeState(
        List<PracticeQuestion> remaining, List<PracticeAnswer> history, int score) {
    public PracticeState {
        remaining = List.copyOf(remaining);
        history = List.copyOf(history);
    }
}
