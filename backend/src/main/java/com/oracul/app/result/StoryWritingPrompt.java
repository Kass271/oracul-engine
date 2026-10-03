package com.oracul.app.result;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.reasoning.ClosedEvidenceMode;
import com.oracul.app.reasoning.ScenarioWindow;
import com.oracul.app.reasoning.UntrustedText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** The STORY_WRITING request (FR-23): constant instructions, trusted settings, the scenario as untrusted data. Pure. */
public final class StoryWritingPrompt {

    static final String RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "Write a short news story from the future about the future event of the scenario in structured-scenario, "
            + "reported on a date inside the story date window given in SETTINGS.",
        "Use only the facts, inferences, speculations and the future event of that scenario. Do not add "
            + "current-world facts that are not facts of the scenario.",
        "Keep present-day facts recognisable as reported facts and everything after the cutoff date recognisably "
            + "hypothetical.",
        "headline: one line of at most 160 characters.",
        "futureDate: the date of the story as yyyy-MM-dd inside the story date window; prefer the future event date.",
        "dateline: ORACUL FUTURE — followed by futureDate written as Month d, yyyy.",
        "body: plain text of 150 to 900 words, paragraphs separated by one blank line, no HTML and no Markdown.",
        "Do not write the labels AI-GENERATED FUTURE SCENARIO or POSSIBLE FUTURE — NOT CURRENT NEWS; ORACUL adds them.",
        "Match the tone to Darkness and Optimism; they never permit invented evidence.");

    public static final String INSTRUCTIONS = ClosedEvidenceMode.INSTRUCTIONS + "\n" + RULES;

    static final String TEXT_FORMAT_JSON = "{\"format\":{\"type\":\"json_schema\",\"name\":\"future_story\","
        + "\"strict\":true,\"schema\":{\"type\":\"object\",\"additionalProperties\":false,"
        + "\"required\":[\"headline\",\"dateline\",\"futureDate\",\"body\"],\"properties\":{"
        + "\"headline\":{\"type\":\"string\"},\"dateline\":{\"type\":\"string\"},\"futureDate\":{\"type\":\"string\"},"
        + "\"body\":{\"type\":\"string\"}}}}}";

    private static final JsonMapper JSON = JsonMapper.builder()
        .changeDefaultPropertyInclusion(i -> i.withValueInclusion(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL))
        .build();
    private static final Object TEXT = JSON.readValue(TEXT_FORMAT_JSON, Map.class);

    private StoryWritingPrompt() {
    }

    public static String inputText(EvidencePack pack, StructuredScenario scenario, StoryRequest req) {
        ScenarioWindow window = ScenarioWindow.of(pack);
        var cfg = pack.getConfiguration();
        List<String> wildcards = new ArrayList<>();
        for (ResearchTopic t : pack.getProfile().getTopics()) {
            String line = UntrustedText.sanitize(t.getLabel()) + " " + Math.round(t.getWeight() * 10);
            if (!"custom".equals(t.getCategory())) {
                wildcards.add(line);
            }
        }
        boolean correction = req.attempt() > 1;
        String sj = JSON.writeValueAsString(scenario).replace("<<<", "‹‹‹").replace(">>>", "›››");
        List<String> lines = new ArrayList<>();
        lines.add("ORACUL REQUEST STORY_WRITING");
        lines.add("SETTINGS");
        lines.add("Attempt: " + req.attempt() + " | Reason: " + (correction ? "STORY_CORRECTION" : "INITIAL"));
        lines.add("Realism: " + cfg.getRealism() + " | Darkness: " + cfg.getDarkness() + " | Optimism: "
            + cfg.getOptimism() + " | Horizon: " + ScenarioWindow.label(cfg.getHorizon()));
        lines.add("Wildcards: " + (wildcards.isEmpty() ? "none" : String.join(" | ", wildcards)));
        lines.add("Cutoff date: " + window.cutoff());
        lines.add("Story date window: after " + window.cutoff() + " and no later than " + window.end());
        lines.add("Future event date: " + scenario.getFutureEvent().getDate());
        lines.add("TASK");
        lines.add("Write the story of the future event of the scenario in structured-scenario under the settings "
            + "above.");
        if (correction) {
            lines.add("Your previous story was invalid. Fix the errors listed in story-errors and return the "
                + "complete story again.");
        }
        UntrustedText.block(lines, "structured-scenario", List.of(sj));
        if (correction) {
            UntrustedText.block(lines, "story-errors", UntrustedText.sanitized(req.storyErrors()));
        }
        lines.add(UntrustedText.TRAILER);
        return String.join("\n", lines);
    }

    public static Map<String, Object> body(String model, EvidencePack pack, StructuredScenario scenario,
                                           StoryRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", INSTRUCTIONS);
        body.put("input", List.of(Map.of("role", "user",
            "content", List.of(Map.of("type", "input_text", "text", inputText(pack, scenario, req))))));
        body.put("text", TEXT);
        body.put("store", false);
        return body;
    }
}
