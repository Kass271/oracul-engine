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

    /** Alternative-run context: futures to avoid and, for a duplicate retry, the rejected title and findings. */
    public record Alternative(List<AvoidedFutures.AvoidedFuture> futuresToAvoid, String rejectedTitle,
                              List<String> duplicateFindings) {
        public static final Alternative NONE = new Alternative(List.of(), null, List.of());
    }

    private static final int MAX_FUTURES = 10;
    private static final int MAX_STEPS = 12;
    private static final int MAX_TEXT = 300;

    private static String cut(String s) {
        String clean = UntrustedText.sanitize(s);
        if (clean.codePointCount(0, clean.length()) > MAX_TEXT) {
            return clean.substring(0, clean.offsetByCodePoints(0, MAX_TEXT)) + "…";
        }
        return clean;
    }

    private static void rawBlock(List<String> out, String name, List<String> content) {
        out.add(UntrustedText.BEGIN + name + "\">>>");
        out.addAll(content);
        out.add(UntrustedText.END);
    }

    public static String inputText(EvidencePack pack, GenerationRequest req) {
        return inputText(pack, req, Alternative.NONE);
    }

    public static String inputText(EvidencePack pack, GenerationRequest req, Alternative alt) {
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
        if (pack.getCore().isEmpty() && pack.getSupporting().isEmpty() && pack.getCounterSignals().isEmpty()) {
            lines.add("The Evidence Pack is empty: no current news could be used. Write a fully speculative scenario: "
                + "factsUsed, inferences and counterSignalsConsidered are empty arrays, the causal chain has only "
                + "SPECULATION steps followed by the single FUTURE_EVENT, and no Evidence ID appears anywhere.");
        }
        boolean guarded = !req.guardViolations().isEmpty()
            || req.reason() == com.oracul.app.api.model.ScenarioAttemptReason.GUARD_REGENERATION;
        boolean corrected = req.reason() == com.oracul.app.api.model.ScenarioAttemptReason.SCHEMA_CORRECTION;
        boolean critiqued = !req.criticIssues().isEmpty()
            || req.reason() == com.oracul.app.api.model.ScenarioAttemptReason.CRITIC_REGENERATION;
        boolean avoiding = !alt.futuresToAvoid().isEmpty();
        boolean duplicate = avoiding && (req.reason() == com.oracul.app.api.model.ScenarioAttemptReason.ALTERNATIVE_DISTINCT
            || !alt.duplicateFindings().isEmpty());
        if (avoiding) {
            lines.add("Follow a different causal path than every future listed in futures-to-avoid; do not paraphrase them.");
        }
        if (duplicate) {
            lines.add("Your previous scenario repeated a future listed in futures-to-avoid. Return a new complete "
                + "scenario with a different future event and at least one different causal step; the problems are "
                + "listed in duplicate-future.");
        }
        if (critiqued) {
            lines.add("Your previous scenario failed ORACUL's critic. Return a new complete scenario that resolves "
                + "the issues listed in critique.");
        }
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
        if (avoiding) {
            List<String> f = new ArrayList<>();
            int n = Math.min(alt.futuresToAvoid().size(), MAX_FUTURES);
            for (int i = 0; i < n; i++) {
                var future = alt.futuresToAvoid().get(i);
                f.add("Future " + (i + 1) + ": " + cut(future.title()));
                List<String> steps = future.steps();
                for (int k = 0; k < Math.min(steps.size(), MAX_STEPS); k++) {
                    f.add("- " + cut(steps.get(k)));
                }
                if (steps.size() > MAX_STEPS) {
                    f.add("- … and " + (steps.size() - MAX_STEPS) + " more steps");
                }
            }
            rawBlock(lines, "futures-to-avoid", f);
        }
        if (duplicate) {
            List<String> d = new ArrayList<>();
            d.add("Rejected future: " + cut(alt.rejectedTitle()));
            for (String finding : alt.duplicateFindings()) {
                d.add(cut(finding));
            }
            rawBlock(lines, "duplicate-future", d);
        }
        if (critiqued) {
            List<String> c = new ArrayList<>();
            for (var issue : req.criticIssues()) {
                c.add(issue.getType().getValue() + " | " + UntrustedText.sanitize(issue.getDescription()));
            }
            UntrustedText.block(lines, "critique", c);
        }
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
        return body(model, pack, req, Alternative.NONE);
    }

    public static Map<String, Object> body(String model, EvidencePack pack, GenerationRequest req, Alternative alt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("instructions", INSTRUCTIONS);
        body.put("input", List.of(Map.of("role", "user",
            "content", List.of(Map.of("type", "input_text", "text", inputText(pack, req, alt))))));
        body.put("text", TEXT);
        body.put("store", false);
        return body;
    }
}
