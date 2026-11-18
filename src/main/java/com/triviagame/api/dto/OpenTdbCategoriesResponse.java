package com.triviagame.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Official category response and persisted category-cache contract. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenTdbCategoriesResponse(
        @JsonProperty("trivia_categories") List<OpenTdbCategoryDto> categories) {}
