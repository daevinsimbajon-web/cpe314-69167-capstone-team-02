package com.triviagame.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Exact OpenTDB response envelope. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenTdbResponse(
        @JsonProperty("response_code") Integer responseCode, List<OpenTdbQuestionDto> results) {}
