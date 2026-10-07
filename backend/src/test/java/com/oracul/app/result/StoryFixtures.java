package com.oracul.app.result;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Story fixtures and prompt constants of slice 09 (future-result.md "Slice 09_future-story"): ST-DEFAULT and variants,
 * BODY(n), the verbatim STORY_WRITING rules and text format. Pure test code: nothing here depends on production classes.
 */
public final class StoryFixtures {

    private StoryFixtures() {}

    public static final String HEADLINE = "Stub headline from the future";

    /** S of StoryWritingPrompt.INSTRUCTIONS = ClosedEvidenceMode.INSTRUCTIONS + "\n" + S (phase 04 styled-story.md FR-65: 12 lines). */
    public static final String STORY_RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "Write the story of the future event of the scenario in structured-scenario, reported on a date inside the story date window given in SETTINGS, in the writing style and the story language given in WRITING STYLE.",
        "Use only the facts, inferences, speculations and the future event of that scenario. Do not add current-world facts that are not facts of the scenario.",
        "Keep present-day facts recognisable as reported facts and everything after the cutoff date recognisably hypothetical.",
        "The scenario's events, dates, actors and causes take priority over the writing style: the style changes only how the future is told, never what happens, when, who acts or why.",
        "Never add events, dates or outcomes that are not in the scenario.",
        "headline: one line of at most 160 characters in the story language.",
        "futureDate: the date of the story as yyyy-MM-dd inside the story date window; prefer the future event date.",
        "dateline: ORACUL FUTURE — followed by futureDate; ORACUL replaces it with its own dateline.",
        "body: plain text of 150 to 900 words in the story language, paragraphs separated by one blank line, no HTML and no Markdown.",
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

    // ---- phase 04: styled stories (styled-story.md "Stubs") ----------------------------------------------------------

    public static final String UK_HEADLINE = "Заголовок із майбутнього";

    /** Paragraph k of UBODY: "Абзац k" followed by 58 x " слово" (60 words). */
    public static String uparagraph(int k) {
        return "Абзац " + k + " слово".repeat(58);
    }

    /** UBODY(n): n paragraphs joined by a blank line (UBODY(3) = 180 words). */
    public static String ubody(int n) {
        StringBuilder sb = new StringBuilder();
        for (int k = 1; k <= n; k++) {
            if (k > 1) sb.append("\n\n");
            sb.append(uparagraph(k));
        }
        return sb.toString();
    }

    /** A Ukrainian body of exactly {@code words} words in paragraphs of at most 60 words ("Абзац k" + " слово"...). */
    public static String ubodyOfWords(int words) {
        StringBuilder sb = new StringBuilder();
        int left = words;
        for (int k = 1; left > 0; k++) {
            int w = Math.min(60, left);
            left -= w;
            if (k > 1) sb.append("\n\n");
            sb.append(w == 1 ? "Абзац" : "Абзац " + k + " слово".repeat(w - 2));
        }
        return sb.toString();
    }

    private static final Pattern STYLE_PROFILE = Pattern.compile("^Style profile: (.+)$", Pattern.MULTILINE);
    private static final Pattern STORY_LANGUAGE = Pattern.compile("^Story language: (.+)$", Pattern.MULTILINE);

    /**
     * The styled STORY_WRITING answer: no WRITING STYLE section = Official / Briefing + English = ST-DEFAULT; any other
     * label L in English "Stub headline from the future — L" + BODY(3); Ukrainian "Заголовок із майбутнього — L" + UBODY(3).
     */
    public static String styled(String inputText) {
        String d = futureEventDate(inputText);
        Matcher p = STYLE_PROFILE.matcher(inputText);
        String label = p.find() ? p.group(1) : "Official / Briefing";
        Matcher l = STORY_LANGUAGE.matcher(inputText);
        boolean uk = l.find() && "Ukrainian".equals(l.group(1));
        if (uk) return json(UK_HEADLINE + " — " + label, d, ubody(3));
        if ("Official / Briefing".equals(label)) return stDefault(d);
        return json(HEADLINE + " — " + label, d, body(3));
    }

    /** Headline of the styled answer for a profile label and a language code. */
    public static String styledHeadline(String label, String language) {
        if ("uk".equals(language)) return UK_HEADLINE + " — " + label;
        return "Official / Briefing".equals(label) ? HEADLINE : HEADLINE + " — " + label;
    }

    public static final String SECTION_PRIORITY =
        "Priority: the scenario's events, dates, actors and causes take priority over this writing style.";
    public static final List<String> OFFICIAL_MODE_LINES = List.of(
        "Write a factual, concise and neutral analytical report about events, consequences, actors, dates and causal relationships, with no literary description and no invented scenes or characters.",
        "Structure the body by short capitalised lead-ins: the first paragraph starts with SITUATION., a middle paragraph with CONSEQUENCES. and the last paragraph with OUTLOOK.");
    public static final String OFFICIAL_UK_SUFFIX = " Write the lead-ins in Ukrainian: СИТУАЦІЯ., НАСЛІДКИ., ПЕРСПЕКТИВА.";
    public static final List<String> NARRATIVE_MODE_LINES = List.of(
        "Write literary prose that evokes the tone and themes of this style profile; atmosphere, scene setting, imagery and invented minor characters or scenes are allowed.",
        "Do not copy or quote the author's text, signature phrases or characters; write original prose that is only inspired by the tone and themes.",
        "Do not add events, dates or outcomes beyond the scenario.");

    /** The WRITING STYLE section as the spec lists it (lines joined by "\n", no trailing newline). */
    public static String styleSection(boolean official, String label, String characteristics, String instructions, boolean uk) {
        List<String> lines = new ArrayList<>();
        lines.add("WRITING STYLE");
        lines.add("Writing mode: " + (official ? "Official / Briefing" : "Narrative"));
        lines.add("Style profile: " + label);
        lines.add("Style characteristics: " + characteristics);
        lines.add("Story language: " + (uk ? "Ukrainian" : "English"));
        lines.add("Style instructions: " + instructions);
        if (official) {
            lines.add(OFFICIAL_MODE_LINES.get(0));
            lines.add(OFFICIAL_MODE_LINES.get(1) + (uk ? OFFICIAL_UK_SUFFIX : ""));
        } else {
            lines.addAll(NARRATIVE_MODE_LINES);
        }
        lines.add(uk ? "Write the headline and the body in Ukrainian." : "Write the headline and the body in English.");
        lines.add(SECTION_PRIORITY);
        return String.join("\n", lines);
    }

    /** Ukrainian genitive month names, month 1..12 (styled-story.md FR-66). */
    public static final List<String> UK_MONTHS = List.of("січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня");

    /** "ORACUL FUTURE — d <genitive month> yyyy" built independently of the production formatter. */
    public static String ukDateline(LocalDate d) {
        return "ORACUL FUTURE — " + d.getDayOfMonth() + " " + UK_MONTHS.get(d.getMonthValue() - 1) + " " + d.getYear();
    }

    /** The phase-01 English dateline "ORACUL FUTURE — Month d, yyyy". */
    public static String enDateline(LocalDate d) {
        return "ORACUL FUTURE — " + java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH).format(d);
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
