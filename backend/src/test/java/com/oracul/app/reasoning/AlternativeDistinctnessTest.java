package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.StructuredScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** generation-runs.md "Slice 17_alternative-future" backend tests: AlternativeDistinctness.findings D1..D9, every row. */
// @trace FR-30
class AlternativeDistinctnessTest {

    private static final String REQUEST_TEXT =
        "Future event date window: after 2026-10-02 and no later than 2031-10-02\nfiller\n";
    private static final String TITLE_1 = "same future event title as future 1";
    private static final String TITLE_2 = "same future event title as future 2";
    private static final String STEPS_1 = "same causal steps as future 1";

    private static final List<String> PARENT_STEPS =
        List.of("Stub fact citing E001.", "Stub inference.", "Stub speculation.", "Stub future event.");
    private static final List<String> OTHER_STEPS = List.of("Stub inference.", "Other step Y.");

    private static Object parent() {
        return ReasoningHarness.avoided("Stub future A", PARENT_STEPS);
    }

    private static Object other() {
        return ReasoningHarness.avoided("Stub other future", OTHER_STEPS);
    }

    private static StructuredScenario sc(String fixtureJson) {
        return ReasoningHarness.scenario(fixtureJson);
    }

    private static StructuredScenario alt1() {
        return sc(ScenarioFixtures.scenarioAlternative(REQUEST_TEXT, 1));
    }

    /** SC-DEFAULT with the given title and the given chain statements. */
    private static StructuredScenario cand(String title, List<String> steps) {
        ObjectNode tree = ReasoningHarness.tree(ScenarioFixtures.scenarioDefault(REQUEST_TEXT));
        ((ObjectNode) tree.get("futureEvent")).put("title", title);
        ArrayNode chain = ReasoningHarness.MAPPER.createArrayNode();
        int order = 1;
        for (String st : steps) {
            ObjectNode n = ReasoningHarness.MAPPER.createObjectNode();
            n.put("order", order++);
            n.put("informationClass", "FACT");
            n.putNull("claimId");
            n.put("statement", st);
            n.set("evidenceIds", ReasoningHarness.MAPPER.createArrayNode());
            n.putNull("year");
            chain.add(n);
        }
        tree.set("causalChain", chain);
        return ReasoningHarness.MAPPER.convertValue(tree, StructuredScenario.class);
    }

    private static Arguments row(String name, StructuredScenario candidate, List<Object> avoided, List<String> expected) {
        return Arguments.of(name, candidate, avoided, expected);
    }

    static Stream<Arguments> rows() {
        List<String> newSteps = List.of("Something new.");
        return Stream.of(
            row("D1 SC-ALT(1) vs parent", alt1(), List.of(parent()), List.of()),
            row("D2 same title, new steps", cand("Stub future A", List.of("Stub alternative speculation 1.")), List.of(parent()), List.of(TITLE_1)),
            row("D3 title with case and whitespace", cand("  stub   FUTURE a ", List.of("Stub alternative speculation 1.")), List.of(parent()), List.of(TITLE_1)),
            row("D4 new title, parent's steps", cand("Stub future A2", PARENT_STEPS), List.of(parent()), List.of(STEPS_1)),
            row("D5 identical", sc(ScenarioFixtures.scenarioDefault(REQUEST_TEXT)), List.of(parent()), List.of(TITLE_1, STEPS_1)),
            row("D6a steps reordered", cand("New title", List.of("Stub future event.", "Stub speculation.", "Stub inference.", "Stub fact citing E001.")),
                List.of(parent()), List.of(STEPS_1)),
            row("D6b only steps 1, 2, 4", cand("New title", List.of("Stub fact citing E001.", "Stub inference.", "Stub future event.")),
                List.of(parent()), List.of(STEPS_1)),
            row("D6c variants with case and spaces", cand("New title", List.of("  STUB inference. ", "stub   speculation.")),
                List.of(parent()), List.of(STEPS_1)),
            row("D7 one new step", cand("New title", List.of("Stub inference.", "Something new.")), List.of(parent()), List.of()),
            row("D7b only a new step", cand("New title", newSteps), List.of(parent()), List.of()),
            row("D8 title of future 2, a new step", cand("Stub other future", List.of("Stub inference.", "Something new.")),
                List.of(parent(), other()), List.of(TITLE_2)),
            row("D8b titles of both futures", cand("stub FUTURE a", List.of("Something new.")),
                List.of(parent(), ReasoningHarness.avoided("Stub future A", "Other step Z.")), List.of(TITLE_1, "same future event title as future 2")),
            row("D9 steps of future 2, one not in the parent's", cand("New title", OTHER_STEPS), List.of(parent(), other()), List.of()));
    }

    // @trace FR-30
    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void findingsMatchTheSpecTable(String name, StructuredScenario candidate, List<Object> avoided, List<String> expected) {
        assertThat(ReasoningHarness.findings(candidate, avoided)).isEqualTo(expected);
    }

    // @trace FR-30
    @Test
    void distinctIffNoFindingsAndTheResultIsAFreshList() {
        List<String> none = ReasoningHarness.findings(alt1(), new ArrayList<>(List.of(parent())));
        assertThat(none).isEmpty();
    }
}
