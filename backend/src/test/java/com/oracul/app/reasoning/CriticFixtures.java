package com.oracul.app.reasoning;

/**
 * Critic fixtures CR-PASS / CR-ICS / CR-CERT and the verbatim SCENARIO_CRITIC constants of slice 10 (scenario-reasoning.md
 * "Slice 10_critic"). Pure test code: nothing here depends on production classes.
 */
public final class CriticFixtures {

    private CriticFixtures() {}

    public static final String CR_PASS = "{\"verdict\":\"PASS\",\"issues\":[]}";
    public static final String ICS_DESCRIPTION = "The scenario ignores the counter-signals of the Evidence Pack.";
    public static final String CR_ICS = "{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"IGNORED_COUNTER_SIGNALS\",\"description\":\""
        + ICS_DESCRIPTION + "\"}]}";
    public static final String CERT_1 = "P1 is stated as a certain fact.";
    public static final String CERT_2 = "The future event comes too early for the causal chain.";
    public static final String CR_CERT = "{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"INAPPROPRIATE_CERTAINTY\",\"description\":\""
        + CERT_1 + "\"},{\"type\":\"UNREALISTIC_TIMELINE\",\"description\":\"" + CERT_2 + "\"}]}";

    public static String fixture(String name) {
        return switch (name) {
            case "CR-PASS" -> CR_PASS;
            case "CR-ICS" -> CR_ICS;
            case "CR-CERT" -> CR_CERT;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** K of ScenarioCriticPrompt.INSTRUCTIONS = ClosedEvidenceMode.INSTRUCTIONS + "\n" + K. */
    public static final String CRITIC_RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "You are the critic of ORACUL. Check the scenario in structured-scenario against the Evidence Pack in evidence-pack and the settings in SETTINGS. Do not rewrite the scenario.",
        "Report one issue for each problem of these types:",
        "UNSUPPORTED_FACTUAL_JUMP: a step of the causal chain does not follow from the facts and inferences before it.",
        "CONTRADICTION: claims of the scenario contradict each other or the Evidence Pack.",
        "UNREALISTIC_TIMELINE: the future event cannot plausibly happen within the time horizon.",
        "IGNORED_COUNTER_SIGNALS: counter-signals of the Evidence Pack are missing from counterSignalsConsidered or are dismissed without reason.",
        "WILDCARD_FORCING: a wildcard is used without a coherent relationship to the evidence.",
        "SETTINGS_MISMATCH: the scenario does not match Realism, Darkness, Optimism, the time horizon or the wildcard intensities.",
        "INAPPROPRIATE_CERTAINTY: inferences, speculations or the future event are stated as certain facts.",
        "verdict: FAIL when you report at least one issue, otherwise PASS with an empty issues list.",
        "description: one or two sentences that name the claim ids or Evidence IDs concerned.");

    public static final String INSTRUCTIONS = ScenarioFixtures.CLOSED_EVIDENCE_MODE + "\n" + CRITIC_RULES;

    /** The exact value of the body key "text". */
    public static final String TEXT_FORMAT_JSON =
        "{\"format\":{\"type\":\"json_schema\",\"name\":\"scenario_critique\",\"strict\":true,\"schema\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"verdict\",\"issues\"],\"properties\":{\"verdict\":{\"type\":\"string\",\"enum\":[\"PASS\",\"FAIL\"]},\"issues\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"type\",\"description\"],\"properties\":{\"type\":{\"type\":\"string\",\"enum\":[\"UNSUPPORTED_FACTUAL_JUMP\",\"CONTRADICTION\",\"UNREALISTIC_TIMELINE\",\"IGNORED_COUNTER_SIGNALS\",\"WILDCARD_FORCING\",\"SETTINGS_MISMATCH\",\"INAPPROPRIATE_CERTAINTY\"]},\"description\":{\"type\":\"string\"}}}}}}}}";
}
