package com.oracul.app.result;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Story fixtures and prompt constants of slice 09 (future-result.md "Slice 09_future-story"): ST-DEFAULT and variants,
 * BODY(n), the verbatim STORY_WRITING rules and text format. Pure test code: nothing here depends on production classes.
 */
public final class StoryFixtures {

    private StoryFixtures() {}

    public static final String HEADLINE = "Stub headline from the future";

    /** S of StoryWritingPrompt.INSTRUCTIONS = ClosedEvidenceMode.INSTRUCTIONS + "\n" + S. */
    public static final String STORY_RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "Write a short news story from the future about the future event of the scenario in structured-scenario, reported on a date inside the story date window given in SETTINGS.",
        "Use only the facts, inferences, speculations and the future event of that scenario. Do not add current-world facts that are not facts of the scenario.",
        "Keep present-day facts recognisable as reported facts and everything after the cutoff date recognisably hypothetical.",
        "headline: one line of at most 160 characters.",
        "futureDate: the date of the story as yyyy-MM-dd inside the story date window; prefer the future event date.",
        "dateline: ORACUL FUTURE — followed by futureDate written as Month d, yyyy.",
        "body: plain text of 150 to 900 words, paragraphs separated by one blank line, no HTML and no Markdown.",
        "Do not write the labels AI-GENERATED FUTURE SCENARIO or POSSIBLE FUTURE — NOT CURRENT NEWS; ORACUL adds them.",
        "Match the tone to Darkness and Optimism; they never permit invented evidence.");

    public static final String INSTRUCTIONS =
        com.oracul.app.reasoning.ScenarioFixtures.CLOSED_EVIDENCE_MODE + "\n" + STORY_RULES;

    /** The exact value of the body key "text". */
    public static final String TEXT_FORMAT_JSON =
        "{\"format\":{\"type\":\"json_schema\",\"name\":\"future_story\",\"strict\":true,\"schema\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"headline\",\"dateline\",\"futureDate\",\"body\"],\"properties\":{\"headline\":{\"type\":\"string\"},\"dateline\":{\"type\":\"string\"},\"futureDate\":{\"type\":\"string\"},\"body\":{\"type\":\"string\"}}}}}";

    public static final String LABEL_1 = "AI-GENERATED FUTURE SCENARIO";
    public static final String LABEL_2 = "POSSIBLE FUTURE — NOT CURRENT NEWS";

    /** Paragraph k: "Stub paragraph k" followed by 57 x " lorem" (60 words). */
    public static String paragraph(int k) {
        return "Stub paragraph " + k + " lorem".repeat(57);
    }

    /** n paragraphs joined by a blank line. */
    public static String body(int n) {
        StringBuilder sb = new StringBuilder();
        for (int k = 1; k <= n; k++) {
            if (k > 1) sb.append("\n\n");
            sb.append(paragraph(k));
        }
        return sb.toString();
    }

    /** BODY(3) with the last paragraph cut to 26 x " lorem" (149 words). */
    public static String body149() {
        return paragraph(1) + "\n\n" + paragraph(2) + "\n\nStub paragraph 3" + " lorem".repeat(26);
    }

    public static String body901() {
        return body(15) + " lorem";
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** JSON text of a story output. */
    public static String json(String headline, String futureDate, String body) {
        return "{\"headline\":" + q(headline) + ",\"dateline\":\"STUB DATELINE\",\"futureDate\":" + q(futureDate) + ",\"body\":" + q(body) + "}";
    }

    /** ST-DEFAULT(d). */
    public static String stDefault(String d) {
        return json(HEADLINE, d, body(3));
    }

    // ---- request-text helpers (stub side) -----------------------------------------------------------------------

    private static final Pattern FUTURE_EVENT_DATE = Pattern.compile("^Future event date: (\\d{4}-\\d{2}-\\d{2})$", Pattern.MULTILINE);
    private static final Pattern CUTOFF_DATE = Pattern.compile("^Cutoff date: (\\d{4}-\\d{2}-\\d{2})$", Pattern.MULTILINE);
    private static final Pattern WINDOW =
        Pattern.compile("^Story date window: after (\\d{4}-\\d{2}-\\d{2}) and no later than (\\d{4}-\\d{2}-\\d{2})$", Pattern.MULTILINE);

    public static String futureEventDate(String inputText) {
        Matcher m = FUTURE_EVENT_DATE.matcher(inputText);
        if (!m.find()) throw new IllegalStateException("no Future event date line in request");
        return m.group(1);
    }

    public static String cutoffDate(String inputText) {
        Matcher m = CUTOFF_DATE.matcher(inputText);
        if (!m.find()) throw new IllegalStateException("no cutoff line in request");
        return m.group(1);
    }

    public static String windowEnd(String inputText) {
        Matcher m = WINDOW.matcher(inputText);
        if (!m.find()) throw new IllegalStateException("no Story date window line in request");
        return m.group(2);
    }

    /**
     * Named story answer for a STORY_WRITING request text. TODAY / LATE / BADDATE have a valid headline and body and a
     * futureDate of the cutoff day / window end + 1 day / 2027-13-01; ST-149 / ST-901 / ST-H161 / ST-HTML / ST-MD
     * carry the futureDate of the future event; ST-149-TODAY is ST-149 with futureDate TODAY; TOOLS has an extra key.
     */
    public static String fixture(String name, String inputText) {
        String d = futureEventDate(inputText);
        return switch (name) {
            case "ST-DEFAULT" -> stDefault(d);
            case "TODAY" -> stDefault(cutoffDate(inputText));
            case "LATE" -> stDefault(LocalDate.parse(windowEnd(inputText)).plusDays(1).toString());
            case "BADDATE" -> stDefault("2027-13-01");
            case "ST-149" -> json(HEADLINE, d, body149());
            case "ST-149-TODAY" -> json(HEADLINE, cutoffDate(inputText), body149());
            case "ST-901" -> json(HEADLINE, d, body901());
            case "ST-H161" -> json("H".repeat(161), d, body(3));
            case "ST-HTML" -> json(HEADLINE, d, body(3) + "\n\n<p>Breaking</p>");
            case "ST-MD" -> json(HEADLINE, d, body(3) + "\n\n## Breaking");
            case "TOOLS" -> stDefault(d).replaceFirst("\\}$", ",\"tools\":[]}");
            case "NOT-JSON" -> "not json";
            case "EMPTY" -> "";
            default -> throw new IllegalArgumentException(name);
        };
    }
}
