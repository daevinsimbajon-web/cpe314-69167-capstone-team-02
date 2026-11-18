package com.triviagame.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.triviagame.dto.opentdb.OpenTdbCategoriesResponse;
import com.triviagame.dto.opentdb.OpenTdbResponse;
import java.io.IOException;

/** Deserializes upstream contracts without interpreting or repairing their contents. */
public final class OpenTdbResponseParser {
  private final ObjectMapper json = new ObjectMapper();

  public OpenTdbResponseParser() {
    json.coercionConfigFor(LogicalType.Textual)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Integer)
        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
  }

  public OpenTdbResponse questions(String content) throws IOException {
    return json.readValue(content, OpenTdbResponse.class);
  }

  public OpenTdbCategoriesResponse categories(String content) throws IOException {
    return json.readValue(content, OpenTdbCategoriesResponse.class);
  }
}
