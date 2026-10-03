package com.oracul.app.reasoning;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.GuardViolation;
import com.oracul.app.api.model.ResearchTopic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** The SCENARIO_GENERATION request (FR-19): constant instructions, trusted settings, untrusted data in blocks. Pure. */
public final class ScenarioGenerationPrompt {

    static final String RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "Information classes: FACT = a statement taken from the Evidence Pack that cites its Evidence IDs; "
            + "INFERENCE = a conclusion drawn from facts (basedOn lists the fact ids); SPECULATION = a clearly "
            + "hypothetical consequence; FUTURE_EVENT = the single future event of the scenario.",
        "Consider at least two candidate futures, evaluate each against the evidence, the settings and the "
            + "counter-signals, and select exactly one.",
        "Build the causal chain from facts through inferences and speculations to the future event. Number the "
            + "steps 1..n. The future event is the last step and carries its year.",
        "Realism 10 means short causal chains and strong evidence; Realism 1 allows a highly imaginative future, "
            + "but never invented current facts.",
        "Darkness and Optimism set the tone of the future; they never permit invented evidence.",
        "Use a wildcard only where the evidence gives it a coherent relationship to the scenario; never force it.",
        "The future event date must lie inside the window given in SETTINGS.",
        "Address the counter-signals of the Evidence Pack in counterSignalsConsidered.");

    public static final String INSTRUCTIONS = ClosedEvidenceMode.INSTRUCTIONS + "\n" + RULES;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Object TEXT = JSON.readValue(ScenarioSchema.TEXT_FORMAT_JSON, Map.class);

    private ScenarioGenerationPrompt() {
    }

    public static String inputText(EvidencePack pack, GenerationRequest req) {
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
        List<String> lines = new ArrayList<>();
        lines.add("ORACUL REQUEST SCENARIO_GENERATION");
        lines.add("SETTINGS");
        lines.add("Attempt: " + req.attempt() + " | Reason: " + req.reason().getValue());
        lines.add("Realism: " + cfg.getRealism() + " | Darkness: " + cfg.getDarkness() + " | Optimism: "
            + cfg.getOptimism() + " | Horizon: " + ScenarioWindow.label(cfg.getHorizon()));
        lines.add("Wildcards: " + (wildcards.isEmpty() ? "none" : String.join(" | ", wildcards)));
        lines.add("Cutoff date: " + window.cutoff());
        lines.add("Future event date window: after " + window.cutoff() + " and no later than " + window.end());
        lines.add("TASK");
        lines.add("Construct one scenario from the Evidence Pack in evidence-pack under the settings above.");
        lines.add("Cite Evidence IDs exactly as written in the pack. Use claim ids F1, F2, … for facts, I1, I2, … "
            + "for inferences and P1, P2, … for speculations.");
        boolean guarded = !req.guardViolations().isEmpty()
            || req.reason() == com.oracul.app.api.model.ScenarioAttemptReason.GUARD_REGENERATION;
        boolean corrected = req.reason() == com.oracul.app.api.model.ScenarioAttemptReason.SCHEMA_CORRECTION;
        if (guarded) {
            lines.add("Your previous scenario failed the Evidence Guard. Return a new complete scenario without the "
                + "problems listed in guard-violations.");
        }
        if (corrected) {
            lines.add("Your previous answer was invalid. Fix the errors listed in schema-errors and return the "
                + "complete scenario again.");
        }
        UntrustedText.block(lines, "evidence-pack", List.of(pack.getPromptText()));
        UntrustedText.block(lines, "custom-wildcards", custom.isEmpty() ? List.of("none") : custom);
        if (guarded) {
            List<String> v = new ArrayList<>();
            for (GuardViolation g : req.guardViolations()) {
                v.add(String.join(" | ", UntrustedText.sanitize(g.getType().getValue()),
                    g.getClaimId() == null ? "-" : UntrustedText.id(g.getClaimId()),
                    g.getEvidenceId() == null ? "-" : UntrustedText.id(g.getEvidenceId()),
                    UntrustedText.sanitize(g.getDetail())));
            }
            UntrustedText.block(lines, "guard-violations", v);
        }
        if (corrected) {
            UntrustedText.block(lines, "schema-errors", UntrustedText.sanitized(req.schemaErrors()));
        }
        lines.add(UntrustedText.TRAILER);
        return String.join("\n", lines);
    }

    public static Map<String, Object> body(String model, EvidencePack pack, GenerationRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", INSTRUCTIONS);
        body.put("input", List.of(Map.of("role", "user",
            "content", List.of(Map.of("type", "input_text", "text", inputText(pack, req))))));
        body.put("text", TEXT);
        body.put("store", false);
        return body;
    }
}
