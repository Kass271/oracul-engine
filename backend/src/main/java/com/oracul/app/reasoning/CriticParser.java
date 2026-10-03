package com.oracul.app.reasoning;

import com.oracul.app.api.model.CriticIssue;
import com.oracul.app.api.model.CriticIssueType;
import com.oracul.app.api.model.CriticVerdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict parser of the critic's output text into a verdict and issues (FR-22). Pure. */
public final class CriticParser {

    public record Critique(CriticVerdict verdict, List<CriticIssue> issues) {
    }

    public record ParseResult(Optional<Critique> critique, List<String> errors) {
    }

    static final int MAX_ISSUES = 10;
    static final int MAX_DESCRIPTION = 300;

    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .build();
    private static final String TYPES = Arrays.stream(CriticIssueType.values()).map(CriticIssueType::getValue)
        .collect(Collectors.joining(", "));

    private static final class Invalid extends RuntimeException {
        Invalid(String message) {
            super(message, null, false, false);
        }
    }

    private CriticParser() {
    }

    public static ParseResult parse(Optional<String> outputText) {
        if (outputText == null || outputText.isEmpty() || outputText.get().isBlank()) {
            return failure("no output text");
        }
        JsonNode root;
        try {
            root = JSON.readTree(outputText.get());
        } catch (RuntimeException e) {
            return failure("output is not valid JSON");
        }
        if (root == null || !root.isObject()) {
            return failure("output is not valid JSON");
        }
        CriticVerdict verdict;
        List<CriticIssue> issues = new ArrayList<>();
        try {
            keys(root, "", List.of("verdict", "issues"));
            JsonNode v = root.get("verdict");
            if (!v.isString()) {
                throw new Invalid("verdict has the wrong type");
            }
            JsonNode arr = root.get("issues");
            if (!arr.isArray()) {
                throw new Invalid("issues has the wrong type");
            }
            List<String> descriptions = new ArrayList<>();
            List<String> typeNames = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                String p = "issues[" + i + "]";
                JsonNode n = arr.get(i);
                if (!n.isObject()) {
                    throw new Invalid(p + " has the wrong type");
                }
                keys(n, p + ".", List.of("type", "description"));
                if (!n.get("type").isString()) {
                    throw new Invalid(p + ".type has the wrong type");
                }
                if (!n.get("description").isString()) {
                    throw new Invalid(p + ".description has the wrong type");
                }
                typeNames.add(n.get("type").asString());
                descriptions.add(n.get("description").asString());
            }
            verdict = switch (v.asString()) {
                case "PASS" -> CriticVerdict.PASS;
                case "FAIL" -> CriticVerdict.FAIL;
                default -> throw new Invalid("verdict must be one of PASS, FAIL");
            };
            for (int i = 0; i < typeNames.size(); i++) {
                CriticIssueType type = null;
                for (CriticIssueType t : CriticIssueType.values()) {
                    if (t.getValue().equals(typeNames.get(i))) {
                        type = t;
                    }
                }
                if (type == null) {
                    throw new Invalid("issues[" + i + "].type must be one of " + TYPES);
                }
                issues.add(new CriticIssue(type, normalize(descriptions.get(i))));
            }
        } catch (Invalid e) {
            return failure(e.getMessage());
        }
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < issues.size(); i++) {
            if (issues.get(i).getDescription().isEmpty()) {
                errors.add("issues[" + i + "].description must not be blank");
            }
        }
        if (verdict == CriticVerdict.FAIL && issues.isEmpty()) {
            errors.add("verdict FAIL needs at least 1 issue");
        }
        if (verdict == CriticVerdict.PASS && !issues.isEmpty()) {
            errors.add("verdict PASS must have no issues");
        }
        if (!errors.isEmpty()) {
            return new ParseResult(Optional.empty(), errors);
        }
        List<CriticIssue> kept = new ArrayList<>(issues.subList(0, Math.min(MAX_ISSUES, issues.size())));
        return new ParseResult(Optional.of(new Critique(verdict, kept)), List.of());
    }

    private static ParseResult failure(String message) {
        return new ParseResult(Optional.empty(), List.of(message));
    }

    private static void keys(JsonNode node, String prefix, List<String> allowed) {
        for (String k : node.propertyNames()) {
            if (!allowed.contains(k)) {
                throw new Invalid("unknown field " + prefix + k);
            }
        }
        for (String k : allowed) {
            JsonNode v = node.get(k);
            if (v == null || v.isNull()) {
                throw new Invalid("missing field " + prefix + k);
            }
        }
    }

    private static String normalize(String s) {
        String clean = s.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}\\s]+", " ").trim();
        if (clean.codePointCount(0, clean.length()) > MAX_DESCRIPTION) {
            return clean.substring(0, clean.offsetByCodePoints(0, MAX_DESCRIPTION)) + "…";
        }
        return clean;
    }
}
