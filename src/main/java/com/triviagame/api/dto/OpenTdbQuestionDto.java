package com.triviagame.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Exact upstream question shape; normalization belongs in the mapper. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenTdbQuestionDto(
        String category,
        String type,
        String difficulty,
        String question,
        @JsonProperty("correct_answer") String correctAnswer,
        @JsonProperty("incorrect_answers") List<String> incorrectAnswers) {}
