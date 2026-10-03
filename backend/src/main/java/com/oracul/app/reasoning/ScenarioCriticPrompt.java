package com.oracul.app.reasoning;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.ScenarioAttemptReason;
import com.oracul.app.api.model.StructuredScenario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** The SCENARIO_CRITIC request (FR-22): constant instructions, trusted settings, untrusted data in blocks. Pure. */
public final class ScenarioCriticPrompt {

    static final String RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "You are the critic of ORACUL. Check the scenario in structured-scenario against the Evidence Pack in "
            + "evidence-pack and the settings in SETTINGS. Do not rewrite the scenario.",
        "Report one issue for each problem of these types:",
        "UNSUPPORTED_FACTUAL_JUMP: a step of the causal chain does not follow from the facts and inferences before it.",
        "CONTRADICTION: claims of the scenario contradict each other or the Evidence Pack.",
        "UNREALISTIC_TIMELINE: the future event cannot plausibly happen within the time horizon.",
        "IGNORED_COUNTER_SIGNALS: counter-signals of the Evidence Pack are missing from counterSignalsConsidered or "
            + "are dismissed without reason.",
        "WILDCARD_FORCING: a wildcard is used without a coherent relationship to the evidence.",
        "SETTINGS_MISMATCH: the scenario does not match Realism, Darkness, Optimism, the time horizon or the "
            + "wildcard intensities.",
        "INAPPROPRIATE_CERTAINTY: inferences, speculations or the future event are stated as certain facts.",
        "verdict: FAIL when you report at least one issue, otherwise PASS with an empty issues list.",
        "description: one or two sentences that name the claim ids or Evidence IDs concerned.");

    public static final String INSTRUCTIONS = ClosedEvidenceMode.INSTRUCTIONS + "\n" + RULES;

    static final String TEXT_FORMAT_JSON = "{\"format\":{\"type\":\"json_schema\",\"name\":\"scenario_critique\","
        + "\"strict\":true,\"schema\":{\"type\":\"object\",\"additionalProperties\":false,"
        + "\"required\":[\"verdict\",\"issues\"],\"properties\":{\"verdict\":{\"type\":\"string\","
        + "\"enum\":[\"PASS\",\"FAIL\"]},\"issues\":{\"type\":\"array\",\"items\":{\"type\":\"object\","
        + "\"additionalProperties\":false,\"required\":[\"type\",\"description\"],\"properties\":{"
        + "\"type\":{\"type\":\"string\",\"enum\":[\"UNSUPPORTED_FACTUAL_JUMP\",\"CONTRADICTION\","
        + "\"UNREALISTIC_TIMELINE\",\"IGNORED_COUNTER_SIGNALS\",\"WILDCARD_FORCING\",\"SETTINGS_MISMATCH\","
        + "\"INAPPROPRIATE_CERTAINTY\"]},\"description\":{\"type\":\"string\"}}}}}}}}";

    private static final JsonMapper JSON = JsonMapper.builder()
        .changeDefaultPropertyInclusion(i -> i.withValueInclusion(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL))
        .build();
    private static final Object TEXT = JSON.readValue(TEXT_FORMAT_JSON, Map.class);

    private ScenarioCriticPrompt() {
    }

    public static String inputText(EvidencePack pack, StructuredScenario scenario, int attempt,
                                   ScenarioAttemptReason reason) {
        ScenarioWindow window = ScenarioWindow.of(pack);
        var cfg = pack.getConfiguration();
        List<String> wildcards = new ArrayList<>();
        List<String> custom = new ArrayList<>();
        for (ResearchTopic t : pack.getProfile().getTopics()) {
            String line = UntrustedText.sanitize(t.getLabel()) + " " + Math.round(t.getWeight() * 10);
            if ("custom".equals(t.getCategory())) {
                custom.add(line);
            } else {
                wildcards.add(line);
            }
        }
        String sj = JSON.writeValueAsString(scenario).replace("<<<", "‹‹‹").replace(">>>", "›››");
        List<String> lines = new ArrayList<>();
        lines.add("ORACUL REQUEST SCENARIO_CRITIC");
        lines.add("SETTINGS");
        lines.add("Attempt: " + attempt + " | Reason: " + reason.getValue());
        lines.add("Realism: " + cfg.getRealism() + " | Darkness: " + cfg.getDarkness() + " | Optimism: "
            + cfg.getOptimism() + " | Horizon: " + ScenarioWindow.label(cfg.getHorizon()));
        lines.add("Wildcards: " + (wildcards.isEmpty() ? "none" : String.join(" | ", wildcards)));
        lines.add("Cutoff date: " + window.cutoff());
        lines.add("Future event date window: after " + window.cutoff() + " and no later than " + window.end());
        lines.add("TASK");
        lines.add("Critique the scenario in structured-scenario against the Evidence Pack in evidence-pack under the "
            + "settings above.");
        UntrustedText.block(lines, "evidence-pack", List.of(pack.getPromptText()));
        UntrustedText.block(lines, "custom-wildcards", custom.isEmpty() ? List.of("none") : custom);
        UntrustedText.block(lines, "structured-scenario", List.of(sj));
        lines.add(UntrustedText.TRAILER);
        return String.join("\n", lines);
    }

    public static Map<String, Object> body(String model, EvidencePack pack, StructuredScenario scenario, int attempt,
                                           ScenarioAttemptReason reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", INSTRUCTIONS);
        body.put("input", List.of(Map.of("role", "user",
            "content", List.of(Map.of("type", "input_text", "text", inputText(pack, scenario, attempt, reason))))));
        body.put("text", TEXT);
        body.put("store", false);
        return body;
    }
}
