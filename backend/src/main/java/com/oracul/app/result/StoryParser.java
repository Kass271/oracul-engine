package com.oracul.app.result;

import com.oracul.app.api.model.FutureStory;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict parser of the model's output text into a FutureStory (FR-23). Pure. */
public final class StoryParser {

    public record ParseResult(Optional<FutureStory> story, List<String> errors, boolean onlyDateErrors) {
    }

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build();
    private static final List<String> FIELDS = List.of("headline", "dateline", "futureDate", "body");
    private static final Pattern HTML = Pattern.compile("<[A-Za-z/!]");
    private static final Pattern WS = Pattern.compile("\\s+");

    private StoryParser() {
    }

    /** Normalized content; date is null when it is not a valid date. */
    record Core(String headline, String body, LocalDate date, List<String> errors, boolean onlyDateErrors) {
    }

    public static ParseResult parse(Optional<String> outputText, LocalDate cutoffDate, LocalDate windowEnd) {
        Core core = core(outputText, cutoffDate, windowEnd);
        if (!core.errors().isEmpty()) {
            return new ParseResult(Optional.empty(), core.errors(), core.onlyDateErrors());
        }
        return new ParseResult(Optional.of(new FutureStory(core.headline(), Datelines.format(core.date()), core.date(),
            core.body())), List.of(), false);
    }

    static Core core(Optional<String> outputText, LocalDate cutoffDate, LocalDate windowEnd) {
        if (outputText == null || outputText.isEmpty() || outputText.get().isBlank()) {
            return fail("no output text");
        }
        JsonNode root;
        try {
            root = JSON.readTree(outputText.get());
        } catch (RuntimeException e) {
            return fail("output is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            return fail("output is not valid JSON");
        }
        for (String name : root.propertyNames()) {
            if (!FIELDS.contains(name)) {
                return fail("unknown field " + com.oracul.app.reasoning.UntrustedText.id(name));
            }
        }
        for (String name : FIELDS) {
            JsonNode v = root.get(name);
            if (v == null || v.isNull()) {
                return fail("missing field " + name);
            }
        }
        for (String name : FIELDS) {
            if (!root.get(name).isString()) {
                return fail(name + " has the wrong type");
            }
        }
        List<String> errors = new ArrayList<>();
        List<String> dateErrors = new ArrayList<>();

        String headline = WS.matcher(root.get("headline").asString()).replaceAll(" ").trim();
        int hl = headline.codePointCount(0, headline.length());
        if (headline.isEmpty()) {
            errors.add("headline must not be blank");
        } else if (hl > 160) {
            errors.add("headline must be at most 160 characters (found " + hl + ")");
        }

        String body = normalizeBody(root.get("body").asString());
        int words = body.isEmpty() ? 0 : WS.split(body).length;
        if (words < 150 || words > 900) {
            errors.add("body must have 150 to 900 words (found " + words + ")");
        }
        if (isMarkup(body)) {
            errors.add("body must be plain text");
        }

        String dateText = root.get("futureDate").asString();
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            dateErrors.add("futureDate is not a valid date");
        }
        if (date != null && (!date.isAfter(cutoffDate) || date.isAfter(windowEnd))) {
            dateErrors.add("futureDate " + date + " must be after " + cutoffDate + " and no later than " + windowEnd);
        }
        boolean onlyDate = errors.isEmpty() && !dateErrors.isEmpty();
        errors.addAll(dateErrors);
        return new Core(headline, body, date, errors, onlyDate);
    }

    private static Core fail(String message) {
        return new Core(null, null, null, List.of(message), false);
    }

    private static boolean isMarkup(String body) {
        if (HTML.matcher(body).find() || body.contains("**") || body.contains("__") || body.contains("`")) {
            return true;
        }
        for (String line : body.split("\n", -1)) {
            if (line.startsWith("#")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeBody(String raw) {
        String s = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> {
            if (cp == '\n' || !Character.isISOControl(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        String[] lines = sb.toString().split("\n", -1);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            int end = line.length();
            while (end > 0 && line.charAt(end - 1) == ' ') {
                end--;
            }
            out.add(line.substring(0, end));
        }
        return String.join("\n", out).trim().replaceAll("\n{3,}", "\n\n");
    }
}
