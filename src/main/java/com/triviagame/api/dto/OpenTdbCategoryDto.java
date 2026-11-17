package com.triviagame.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One official category object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenTdbCategoryDto(Integer id, String name) {}
