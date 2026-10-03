package com.oracul.app.reasoning;

import static com.oracul.app.reasoning.ReasoningHarness.MAPPER;
import static com.oracul.app.reasoning.ReasoningHarness.parse;
import static com.oracul.app.reasoning.ReasoningHarness.tree;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.StructuredScenario;
import com.oracul.app.reasoning.ReasoningHarness.Parsed;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** scenario-reasoning.md "Slice 08_validated-scenario" FR-20: the strict parser, rows 1-6 and the structural rules. */
// @trace FR-20
class StructuredScenarioParserTest {

    private static final String D = ScenarioFixtures.DEFAULT_D;

    private static String v4() {
        return ScenarioFixtures.scV4(D);
    }

    private static String mutated(Consumer<ObjectNode> change) {
        ObjectNode n = tree(v4());
        change.accept(n);
        return MAPPER.writeValueAsString(n);
    }

    private static ObjectNode at(ObjectNode root, String array, int i) {
        return (ObjectNode) ((ArrayNode) root.get(array)).get(i);
    }

    private static void assertErrors(String text, String... errors) {
        Parsed p = parse(text);
        assertThat(p.errors()).containsExactly(errors);
        assertThat(p.scenario()).as("no scenario with errors").isEmpty();
    }

    @Test
    void aValidScenarioParsesWithAllFieldsEqualAndNullsAbsent() {
        Parsed p = parse(v4());
        assertThat(p.errors()).isEmpty();
        StructuredScenario s = p.scenario().orElseThrow();
        assertThat(ReasoningHarness.comparable(s)).isEqualTo(ReasoningHarness.comparable(v4()));
        assertThat(s.getCandidateFutures()).hasSize(2);
        assertThat(s.getFactsUsed()).extracting("id").containsExactly("F1", "F2");
        assertThat(s.getCausalChain()).hasSize(5);
        assertThat(s.getCausalChain().get(4).getClaimId()).as("step 5 claimId absent").isNull();
        for (int i = 0; i < 4; i++) assertThat(s.getCausalChain().get(i).getYear()).as("step " + (i + 1) + " year").isNull();
        assertThat(s.getCausalChain().get(4).getYear()).isEqualTo(2027);
        assertThat(s.getFutureEvent().getDate()).hasToString(D);
    }

    @Test
    void anEmptyUnknownsListIsValid() {
        assertThat(parse(v4()).errors()).isEmpty();
        assertThat(parse(mutated(n -> ((ArrayNode) n.get("unknowns")).add("The exact date of the strike."))).errors()).isEmpty();
    }

    // row 1
    @Test
    void noOutputTextIsAnError() {
        Parsed empty = parse(Optional.empty());
        assertThat(empty.errors()).containsExactly("no output text");
        assertThat(empty.scenario()).isEmpty();
        assertErrors("", "no output text");
        assertErrors("   \n ", "no output text");
    }

    // row 2
    @ParameterizedTest(name = "not valid JSON: [{0}]")
    @ValueSource(strings = {"not json", "[]", "\"text\"", "42", "{\"candidateFutures\":"})
    void textThatIsNotAJsonObjectIsInvalidJson(String text) {
        assertErrors(text, "output is not valid JSON");
    }

    @Test
    void trailingGarbageIsInvalidJson() {
        assertErrors(v4() + " garbage", "output is not valid JSON");
        assertErrors(v4() + "{}", "output is not valid JSON");
    }

    // row 3
    @Test
    void anUnknownTopLevelKeyIsRejected() {
        assertErrors(mutated(n -> n.set("tools", MAPPER.createArrayNode())), "unknown field tools");
    }

    @Test
    void anUnknownNestedKeyIsRejectedWithItsPath() {
        assertErrors(mutated(n -> at(n, "factsUsed", 0).put("source", "x")), "unknown field factsUsed[0].source");
    }

    // row 4
    @Test
    void aMissingRequiredKeyIsReportedWithItsPath() {
        assertErrors(mutated(n -> n.remove("counterSignalsConsidered")), "missing field counterSignalsConsidered");
        assertErrors(mutated(n -> n.remove("futureEvent")), "missing field futureEvent");
        assertErrors(mutated(n -> at(n, "causalChain", 1).remove("statement")), "missing field causalChain[1].statement");
    }

    @Test
    void aNullRequiredValueCountsAsMissing() {
        assertErrors(mutated(n -> at(n, "factsUsed", 0).putNull("statement")), "missing field factsUsed[0].statement");
        assertErrors(mutated(n -> at(n, "inferences", 0).putNull("basedOn")), "missing field inferences[0].basedOn");
    }

    @Test
    void claimIdAndYearMayBeNullButNotMissing() {
        assertThat(parse(v4()).errors()).isEmpty(); // SC-V4 carries explicit nulls for both
        assertErrors(mutated(n -> at(n, "causalChain", 0).remove("claimId")), "missing field causalChain[0].claimId");
        assertErrors(mutated(n -> at(n, "causalChain", 0).remove("year")), "missing field causalChain[0].year");
    }

    // row 5
    @Test
    void aWrongJsonTypeIsReportedWithItsPath() {
        assertErrors(mutated(n -> at(n, "causalChain", 0).put("order", "1")), "causalChain[0].order has the wrong type");
        assertErrors(mutated(n -> at(n, "candidateFutures", 0).put("selected", "yes")), "candidateFutures[0].selected has the wrong type");
        assertErrors(mutated(n -> at(n, "factsUsed", 0).put("evidenceIds", "E001")), "factsUsed[0].evidenceIds has the wrong type");
    }

    @Test
    void aFractionalOrderIsNotCoercedToAnInteger() {
        assertErrors(mutated(n -> at(n, "causalChain", 0).put("order", 1.5)), "causalChain[0].order has the wrong type");
    }

    // row 6
    @Test
    void anUnknownInformationClassIsRejected() {
        assertErrors(mutated(n -> at(n, "causalChain", 0).put("informationClass", "FOO")),
            "causalChain[0].informationClass must be one of FACT, INFERENCE, SPECULATION, FUTURE_EVENT");
    }

    // structural rules
    @Test
    void aSingleCandidateFutureIsRejected() {
        assertErrors(mutated(n -> ((ArrayNode) n.get("candidateFutures")).remove(1)),
            "candidateFutures must contain at least 2 items");
    }

    @Test
    void exactlyOneCandidateMustBeSelected() {
        assertErrors(mutated(n -> at(n, "candidateFutures", 1).put("selected", true)),
            "candidateFutures must have exactly 1 selected item");
        assertErrors(mutated(n -> at(n, "candidateFutures", 0).put("selected", false)),
            "candidateFutures must have exactly 1 selected item");
    }

    @Test
    void aCausalChainNeedsAtLeastTwoSteps() {
        assertErrors(mutated(n -> {
            ArrayNode chain = (ArrayNode) n.get("causalChain");
            while (chain.size() > 1) chain.remove(1);
        }), "causalChain must contain at least 2 items");
    }

    @Test
    void blankTextsAreRejectedInDocumentOrder() {
        assertErrors(mutated(n -> at(n, "factsUsed", 1).put("statement", "  ")), "factsUsed[1].statement must not be blank");
        assertErrors(mutated(n -> ((ObjectNode) n.get("futureEvent")).put("title", "")), "futureEvent.title must not be blank");
        assertErrors(mutated(n -> {
            at(n, "candidateFutures", 0).put("title", " ");
            ((ObjectNode) n.get("futureEvent")).put("summary", "");
        }), "candidateFutures[0].title must not be blank", "futureEvent.summary must not be blank");
    }

    @Test
    void claimIdsMustMatchTheirPrefix() {
        assertErrors(mutated(n -> at(n, "factsUsed", 0).put("id", "X1")), "factsUsed[0].id must match F<n>");
        assertErrors(mutated(n -> at(n, "inferences", 0).put("id", "I0")), "inferences[0].id must match I<n>");
        assertErrors(mutated(n -> at(n, "speculations", 0).put("id", "P0")), "speculations[0].id must match P<n>");
    }

    @Test
    void duplicateClaimIdsAreRejected() {
        assertErrors(mutated(n -> at(n, "factsUsed", 1).put("id", "F1")), "duplicate claim id F1");
    }

    @Test
    void theFutureEventDateMustBeAnIsoDate() {
        assertErrors(mutated(n -> ((ObjectNode) n.get("futureEvent")).put("date", "2027-13-01")),
            "futureEvent.date is not a valid date");
        assertErrors(mutated(n -> ((ObjectNode) n.get("futureEvent")).put("date", "March 2027")),
            "futureEvent.date is not a valid date");
    }

    @Test
    void severalStructuralErrorsAreAllReportedInTheSpecifiedOrder() {
        String text = mutated(n -> {
            ((ArrayNode) n.get("candidateFutures")).remove(1);
            at(n, "factsUsed", 0).put("id", "X1");
            ((ObjectNode) n.get("futureEvent")).put("date", "2027-13-01");
        });
        assertErrors(text, "candidateFutures must contain at least 2 items", "factsUsed[0].id must match F<n>",
            "futureEvent.date is not a valid date");
    }

    @Test
    void evidenceIdsAndChainSemanticsAreNotParserRules() {
        // E099 and a chain that goes back in class are Evidence Guard matters; the parser accepts them
        String text = mutated(n -> {
            at(n, "factsUsed", 0).set("evidenceIds", MAPPER.createArrayNode().add("E099"));
            at(n, "causalChain", 2).put("informationClass", "FACT");
        });
        Parsed p = parse(text);
        assertThat(p.errors()).isEmpty();
        assertThat(p.scenario()).isPresent();
    }

    @Test
    void exactlyOneOfScenarioAndErrorsIsNonEmpty() {
        Parsed ok = parse(v4());
        assertThat(ok.scenario()).isPresent();
        assertThat(ok.errors()).isEmpty();
        Parsed bad = parse("not json");
        assertThat(bad.scenario()).isEmpty();
        assertThat(bad.errors()).isNotEmpty();
        assertThat(List.copyOf(bad.errors())).hasSize(1);
    }
}
