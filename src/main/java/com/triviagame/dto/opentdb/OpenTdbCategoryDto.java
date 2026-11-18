package com.triviagame.dto.opentdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One official category object. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenTdbCategoryDto(Integer id, String name) {}
