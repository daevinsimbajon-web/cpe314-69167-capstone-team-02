package com.triviagame.online.practice;

import java.util.List;
import java.util.UUID;

/** Public practice state; the next question's correct answer remains on the server. */
public record PracticeView(
        UUID id,
        int version,
        int score,
        int answered,
        int total,
        String question,
        List<String> choices,
        String type,
        boolean canUndo,
        String feedback,
        Boolean lastCorrect,
        Integer lastPoints) {}
