
package com.triviagame.api;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class QuestionParser {

    private static final ObjectMapper JSON =
            new ObjectMapper();

    private static final Pattern ENTITY =
            Pattern.compile(
                    "&(#(?:[xX][0-9a-fA-F]+|[0-9]+)"
                    + "|[A-Za-z][A-Za-z0-9]+);"
            );

    private static final Pattern PERCENT =
            Pattern.compile("%[0-9a-fA-F]{2}");

    public List<BankQuestion> parse(
            String input) throws IOException {

        return parse(input, false);
    }

    public List<BankQuestion> parse(
            String input,
            boolean url3986) throws IOException {

        JsonNode root = JSON.readTree(input);

        if (root == null) {
            throw new IOException("Empty JSON document.");
        }

        JsonNode questions;

        if (root.isArray()) {
            questions = root;

        } else if (root.isObject()) {

            if (root.has("response_code")
                    && root.get("response_code").asInt(-1) != 0) {

                throw new IOException(
                        "OpenTDB response_code="
                        + root.get("response_code").asInt()
                        + " (no matching questions, "
                        + "invalid filters or rate limit)"
                );
            }

            questions = root.has("results")
                    ? root.get("results")
                    : root.get("questions");

        } else {
            throw new IOException(
                    "Expected a JSON object or array."
            );
        }

        if (questions == null || !questions.isArray()) {
            throw new IOException(
                    "Expected an array named results or questions."
            );
        }

        boolean encoded =
                url3986
                || (root.isObject()
                && root.has("response_code")
                && looksPercentEncoded(questions));

        List<BankQuestion> output =
                new ArrayList<>();

        int index = 0;

        for (JsonNode item : questions) {
            index++;

            try {
                String category = text(
                        item, encoded, "category"
                );

                String question = text(
                        item, encoded, "question", "text"
                );

                String correct = text(
                        item, encoded,
                        "correct_answer", "correctAnswer"
                );

                String difficulty = text(
                        item, encoded, "difficulty"
                );

                String rawType = text(
                        item, false,
                        "type", "question_type", "questionType"
                );

                String type = switch (
                        rawType.toLowerCase(Locale.ROOT)
                ) {
                    case "multiple", "multiple_choice" ->
                            "MULTIPLE_CHOICE";

                    case "boolean", "true_false" ->
                            "TRUE_FALSE";

                    default -> throw new IllegalArgumentException(
                            "Unknown question type: " + rawType
                    );
                };

                JsonNode wrongArray = field(
                        item,
                        "incorrect_answers",
                        "incorrectAnswers"
                );

                if (wrongArray == null
                        || !wrongArray.isArray()) {
                    throw new IllegalArgumentException(
                            "incorrect_answers must be an array."
                    );
                }

                List<String> wrong =
                        new ArrayList<>();

                for (JsonNode answer : wrongArray) {
                    if (!answer.isTextual()) {
                        throw new IllegalArgumentException(
                                "Answer must be text."
                        );
                    }

                    wrong.add(
                            decode(
                                    answer.asText(),
                                    encoded
                            )
                    );
                }

                output.add(
                        new BankQuestion(
                                category,
                                question,
                                correct,
                                wrong,
                                difficulty,
                                type,
                                externalId(item)
                        )
                );

            } catch (IllegalArgumentException ex) {
                throw new IOException(
                        "Invalid question at index "
                        + index + ": " + ex.getMessage(),
                        ex
                );
            }
        }

        return List.copyOf(output);
    }

    private static String externalId(JsonNode item) {
        JsonNode id = field(item, "external_id", "externalId", "id");
        if (id == null) {
            return null;
        }
        if (!id.isTextual() && !id.isIntegralNumber()) {
            throw new IllegalArgumentException("External ID must be text or an integer.");
        }
        return id.asText();
    }

    private static JsonNode field(
            JsonNode item,
            String... keys) {

        if (item == null || !item.isObject()) {
            return null;
        }

        for (String key : keys) {
            if (item.hasNonNull(key)) {
                return item.get(key);
            }
        }

        return null;
    }

    private static String text(
            JsonNode item,
            boolean encoded,
            String... keys) {

        JsonNode node = field(item, keys);

        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(
                    "Missing field: " + keys[0]
            );
        }

        return decode(
                node.asText(),
                encoded
        );
    }

    private static boolean looksPercentEncoded(
            JsonNode items) {

        for (JsonNode item : items) {
            JsonNode question = field(
                    item,
                    "question", "text"
            );

            if (question != null
                    && question.isTextual()
                    && PERCENT.matcher(
                            question.asText()
                    ).find()) {
                return true;
            }
        }

        return false;
    }

    private static String decode(
            String value,
            boolean encoded) {

        if (value == null) {
            return "";
        }

        if (encoded) {
            try {
                value = URLDecoder.decode(
                        value,
                        StandardCharsets.UTF_8
                );

            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Invalid percent encoding.",
                        ex
                );
            }
        }

        return repairMojibake(
                decodeHtml(value)
        );
    }

    public static String displayText(
            String text) {

        if (text == null) {
            return "";
        }

        if (PERCENT.matcher(text).find()) {
            try {
                text = URLDecoder.decode(
                        text,
                        StandardCharsets.UTF_8
                );
            } catch (IllegalArgumentException ignored) {
                // Keep the original text.
            }
        }

        return repairMojibake(
                decodeHtml(text)
        );
    }

    private static String repairMojibake(
            String value) {

        if (!(value.contains("Ã")
                || value.contains("Â")
                || value.contains("â€"))) {
            return value;
        }

        try {
            return new String(
                    value.getBytes(
                            Charset.forName("windows-1252")
                    ),
                    StandardCharsets.UTF_8
            );
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String decodeHtml(
            String source) {

        String result = source;

        for (int pass = 0; pass < 2; pass++) {
            Matcher matcher =
                    ENTITY.matcher(result);

            StringBuffer buffer =
                    new StringBuffer();

            while (matcher.find()) {
                String replacement = entityValue(
                        matcher.group(1)
                );

                matcher.appendReplacement(
                        buffer,
                        Matcher.quoteReplacement(
                                replacement == null
                                        ? matcher.group()
                                        : replacement
                        )
                );
            }

            matcher.appendTail(buffer);
            result = buffer.toString();
        }

        return Normalizer.normalize(
                result,
                Normalizer.Form.NFC
        );
    }

    private static String entityValue(
            String key) {

        if (key.startsWith("#")) {
            try {
                boolean hex =
                        key.length() > 2
                        && (key.charAt(1) == 'x'
                        || key.charAt(1) == 'X');

                int point = Integer.parseInt(
                        key.substring(hex ? 2 : 1),
                        hex ? 16 : 10
                );

                return Character.isValidCodePoint(point)
                        ? new String(
                                Character.toChars(point)
                        )
                        : null;

            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Entity names are case-sensitive (e.g. Eacute and eacute).
        return switch (key) {
            case "amp", "AMP" -> "&";
            case "quot", "QUOT" -> "\"";
            case "apos" -> "'";
            case "rsquo" -> "\u2019";
            case "lsquo" -> "\u2018";
            case "ldquo" -> "\u201c";
            case "rdquo" -> "\u201d";
            case "lt", "LT" -> "<";
            case "gt", "GT" -> ">";
            case "nbsp" -> " ";
            case "ndash" -> "–";
            case "mdash" -> "—";
            case "hellip" -> "…";
            case "eacute" -> "é";
            case "Eacute" -> "É";
            case "egrave" -> "è";
            case "Egrave" -> "È";
            case "ouml" -> "ö";
            case "Ouml" -> "Ö";
            case "uuml" -> "ü";
            case "Uuml" -> "Ü";
            case "auml" -> "ä";
            case "Auml" -> "Ä";
            case "ntilde" -> "ñ";
            case "Ntilde" -> "Ñ";
            case "copy" -> "©";
            case "reg" -> "®";
            default -> null;
        };
    }
}
