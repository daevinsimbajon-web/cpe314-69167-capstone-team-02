package com.triviagame.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestionParser {

    private static final ObjectMapper JSON =
            new ObjectMapper();

    public List<BankQuestion> parse(String jsonText)
            throws IOException {
        return parse(jsonText, false);
    }

    public List<BankQuestion> parse(
            String jsonText, boolean url3986)
            throws IOException {

        JsonNode root = JSON.readTree(jsonText);

        if (root == null) {
            throw new IOException("Empty JSON document");
        }

        JsonNode items;

        if (root.isArray()) {
            items = root;
        } else if (root.isObject()) {

            if (root.has("response_code") &&
                    root.get("response_code").asInt(-1) != 0) {
                throw new IOException(
                    "OpenTDB response_code="
                    + root.get("response_code").asInt()
                    + " (no questions, invalid parameters, or rate limit)"
                );
            }

            items = root.has("results")
                    ? root.get("results")
                    : root.get("questions");

        } else {
            throw new IOException(
                "JSON root must be an object or array"
            );
        }

        if (items == null || !items.isArray()) {
            throw new IOException(
                "Expected a JSON array named results or questions"
            );
        }

        boolean encoded = url3986 ||
                (root.isObject()
                && root.has("response_code")
                && hasPercentEncodedQuestion(items));

        List<BankQuestion> parsed = new ArrayList<>();

        int index = 0;

        for (JsonNode item : items) {
            index++;

            try {
                String category = string(
                    item, encoded, "category"
                );

                String text = string(
                    item, encoded, "question", "text"
                );

                String answer = string(
                    item, encoded, "correct_answer", "correctAnswer"
                );

                String difficulty = string(
                    item, encoded, "difficulty"
                );

                String typeRaw = string(
                    item, false,
                    "type", "question_type", "questionType"
                );

                String type = switch (typeRaw.toLowerCase()) {
                    case "multiple", "multiple_choice"
                        -> "MULTIPLE_CHOICE";

                    case "boolean", "true_false"
                        -> "TRUE_FALSE";

                    default -> throw new IllegalArgumentException(
                        "Unknown question type: " + typeRaw
                    );
                };

                JsonNode incorrect = field(
                    item, "incorrect_answers", "incorrectAnswers"
                );

                if (incorrect == null || !incorrect.isArray()) {
                    throw new IllegalArgumentException(
                        "incorrect_answers must be an array"
                    );
                }

                List<String> distractors = new ArrayList<>();

                for (JsonNode value : incorrect) {
                    if (!value.isTextual()) {
                        throw new IllegalArgumentException(
                            "Answer must be text"
                        );
                    }
                    distractors.add(
                        decode(value.asText(), encoded)
                    );
                }

                parsed.add(new BankQuestion(
                    category,
                    text,
                    answer,
                    distractors,
                    difficulty,
                    type
                ));

            } catch (IllegalArgumentException ex) {
                throw new IOException(
                    "Invalid question at JSON index "
                    + index + ": " + ex.getMessage(), ex
                );
            }
        }

        return List.copyOf(parsed);
    }

    private static JsonNode field(
            JsonNode item, String... alternatives) {

        if (item == null || !item.isObject()) {
            return null;
        }

        for (String key : alternatives) {
            if (item.hasNonNull(key)) {
                return item.get(key);
            }
        }

        return null;
    }

    private static String string(
            JsonNode item, boolean encoded, String... keys) {

        JsonNode value = field(item, keys);

        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(
                "Missing text field: " + keys[0]
            );
        }

        return decode(value.asText(), encoded);
    }

    private static boolean hasPercentEncodedQuestion(
            JsonNode items) {

        for (JsonNode item : items) {
            JsonNode text = field(item, "question", "text");

            return text != null
                && text.isTextual()
                && Pattern.compile("%[0-9A-Fa-f]{2}")
                    .matcher(text.asText())
                    .find();
        }

        return false;
    }

    private static final Pattern HTML_ENTITY =
            Pattern.compile(
                "&(?:amp|quot|apos|lt|gt|#(?:[0-9]+|[xX][0-9a-fA-F]+));"
            );

    private static String decode(
            String value, boolean encoded) {

        if (encoded) {
            try {
                return URLDecoder.decode(
                    value, StandardCharsets.UTF_8
                );
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                    "Invalid percent-encoded OpenTDB text", ex
                );
            }
        }

        Matcher matcher = HTML_ENTITY.matcher(value);
        StringBuilder decoded = new StringBuilder();

        while (matcher.find()) {
            String entity = matcher.group();

            String replacement = switch (entity) {
                case "&amp;" -> "&";
                case "&quot;" -> "\"";
                case "&apos;" -> "'";
                case "&lt;" -> "<";
                case "&gt;" -> ">";

                default -> {
                    try {
                        String numeric = entity.substring(
                            2, entity.length() - 1
                        );

                        int codePoint =
                            numeric.startsWith("x")
                            || numeric.startsWith("X")
                            ? Integer.parseInt(
                                numeric.substring(1), 16
                            )
                            : Integer.parseInt(numeric);

                        yield Character.isValidCodePoint(codePoint)
                            ? new String(Character.toChars(codePoint))
                            : entity;

                    } catch (IllegalArgumentException ex) {
                        yield entity;
                    }
                }
            };

            matcher.appendReplacement(
                decoded,
                Matcher.quoteReplacement(replacement)
            );
        }

        matcher.appendTail(decoded);

        return decoded.toString();
    }
}