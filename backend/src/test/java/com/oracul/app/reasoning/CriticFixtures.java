package com.oracul.app.reasoning;

/**
 * Critic fixtures CR-PASS / CR-JUMP / CR-ICS (legacy) / CR-CERT and the verbatim SCENARIO_CRITIC constants of slice 10 (scenario-reasoning.md
 * "Slice 10_critic"). Pure test code: nothing here depends on production classes.
 */
public final class CriticFixtures {

    private CriticFixtures() {}

    public static final String CR_PASS = "{\"verdict\":\"PASS\",\"issues\":[]}";
    public static final String ICS_DESCRIPTION = "The scenario ignores the counter-signals of the Evidence Pack.";
    public static final String CR_ICS = "{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"IGNORED_COUNTER_SIGNALS\",\"description\":\""
        + ICS_DESCRIPTION + "\"}]}";
    /** CR-JUMP (FR-58): the standard "critic fails once" answer; replaces CR-ICS, which is now a rejected legacy answer. */
    public static final String JUMP_DESCRIPTION = "Step 3 does not follow from the facts and inferences before it.";
    public static final String CR_JUMP = "{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"UNSUPPORTED_FACTUAL_JUMP\",\"description\":\""
        + JUMP_DESCRIPTION + "\"}]}";
    public static final String CERT_1 = "P1 is stated as a certain fact.";
    public static final String CERT_2 = "The future event comes too early for the causal chain.";
    public static final String CR_CERT = "{\"verdict\":\"FAIL\",\"issues\":[{\"type\":\"INAPPROPRIATE_CERTAINTY\",\"description\":\""
        + CERT_1 + "\"},{\"type\":\"UNREALISTIC_TIMELINE\",\"description\":\"" + CERT_2 + "\"}]}";

    public static String fixture(String name) {
        return switch (name) {
            case "CR-PASS" -> CR_PASS;
            case "CR-ICS" -> CR_ICS;
            case "CR-JUMP" -> CR_JUMP;
            case "CR-CERT" -> CR_CERT;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** K of FR-58 step 3: ScenarioCriticPrompt.RULES, joined by \n; INSTRUCTIONS = StartingConditions.INSTRUCTIONS + "\n" + K. */
    public static final String CRITIC_RULES = String.join("\n",
        "Return only JSON matching the schema.",
        "You are the critic of ORACUL. Check the scenario in structured-scenario against the Evidence Pack in evidence-pack and the settings in SETTINGS. Do not rewrite the scenario.",
        "The scenario is meant to extrapolate from the sources according to the settings. A strong, extreme or unlikely development that matches the wildcard levels, Darkness, Optimism, Realism and the time horizon is intended: never report it as wildcard forcing, as an unrealistic timeline or outcome, or as a settings mismatch.",
        "Report one issue for each problem of these types:",
        "UNSUPPORTED_FACTUAL_JUMP: a claim about the present is not supported by the Evidence Pack, or a step of the causal chain does not follow from the steps before it.",
        "CONTRADICTION: claims of the scenario contradict each other or the Evidence Pack.",
        "UNREALISTIC_TIMELINE: the future event cannot happen within the time horizon even at the intensity the settings ask for.",
        "WILDCARD_FORCING: a wildcard appears without any causal connection to the rest of the scenario.",
        "SETTINGS_MISMATCH: the scenario is milder, weaker or in another direction than the wildcard levels, Darkness, Optimism, Realism or the time horizon ask for.",
        "INAPPROPRIATE_CERTAINTY: inferences, speculations or the future event are stated as certain facts.",
        "verdict: FAIL when you report at least one issue, otherwise PASS with an empty issues list.",
        "description: one or two sentences that name the claim ids or Evidence IDs concerned.");

    public static final String INSTRUCTIONS = ScenarioFixtures.STARTING_CONDITIONS + "\n" + CRITIC_RULES;

    /** The exact value of the body key "text". */
    public static final String TEXT_FORMAT_JSON =
        "{\"format\":{\"type\":\"json_schema\",\"name\":\"scenario_critique\",\"strict\":true,\"schema\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"verdict\",\"issues\"],\"properties\":{\"verdict\":{\"type\":\"string\",\"enum\":[\"PASS\",\"FAIL\"]},\"issues\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"type\",\"description\"],\"properties\":{\"type\":{\"type\":\"string\",\"enum\":[\"UNSUPPORTED_FACTUAL_JUMP\",\"CONTRADICTION\",\"UNREALISTIC_TIMELINE\",\"WILDCARD_FORCING\",\"SETTINGS_MISMATCH\",\"INAPPROPRIATE_CERTAINTY\"]},\"description\":{\"type\":\"string\"}}}}}}}}";
}
