package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rows #8-#12 and #14 of scenario-reasoning.md "Slice 08_validated-scenario": Evidence Guard, regeneration and rejection. */
// @trace FR-21
class EvidenceGuardIT extends AbstractReasoningIT {

    private static final String F1_E099 =
        "{\"type\":\"UNKNOWN_EVIDENCE_ID\",\"claimId\":\"F1\",\"evidenceId\":\"E099\","
            + "\"detail\":\"F1 cites E099, which is not in the Evidence Pack\",\"action\":\"REMOVED\"}";
    private static final String F2_NOEV =
        "{\"type\":\"FACT_WITHOUT_EVIDENCE\",\"claimId\":\"F2\",\"detail\":\"F2 cites no Evidence ID\",\"action\":\"REMOVED\"}";
    private static final String I1_REMOVED =
        "{\"type\":\"UNSUPPORTED_INFERENCE\",\"claimId\":\"I1\",\"detail\":\"I1 is based only on removed facts\",\"action\":\"REMOVED\"}";
    private static final String CHAIN_NO_FACT =
        "{\"type\":\"CAUSAL_CHAIN_INVALID\",\"detail\":\"causal chain: no FACT step remains\",\"action\":\"REGENERATION_REQUESTED\"}";
    private static final List<String> G4_LINES = List.of(
        "UNKNOWN_EVIDENCE_ID | F1 | E099 | F1 cites E099, which is not in the Evidence Pack",
        "FACT_WITHOUT_EVIDENCE | F2 | - | F2 cites no Evidence ID",
        "UNSUPPORTED_INFERENCE | I1 | - | I1 is based only on removed facts",
        "CAUSAL_CHAIN_INVALID | - | - | causal chain: no FACT step remains");

    @SuppressWarnings("unchecked")
    private void assertCleanedWithoutF2(Map<String, Object> rec) {
        Map<String, Object> s = (Map<String, Object>) rec.get("structuredScenario");
        assertThat(list(s.get("factsUsed")).stream().map(f -> f.get("id")).toList()).containsExactly("F1");
        assertThat(claimIds(s)).containsExactly("F1", "I1", "P1", null);
        assertThat(list(s.get("causalChain")).stream().map(c -> c.get("order")).toList()).containsExactly(1, 2, 3, 4);
    }

    // #8
    @Test
    void aFactCitingAnUnknownEvidenceIdIsRemovedAndTheScenarioContinues() throws Exception {
        scriptScenario(fx("SC-E099"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        assertThat(requests(GEN)).hasSize(1);
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertThat(rec.get("guardReports")).isEqualTo(jsonOf("[{\"outcome\":\"PASS_WITH_REMOVALS\",\"violations\":[{\"type\":\"UNKNOWN_EVIDENCE_ID\","
            + "\"claimId\":\"F2\",\"evidenceId\":\"E099\",\"detail\":\"F2 cites E099, which is not in the Evidence Pack\","
            + "\"action\":\"REMOVED\"}],\"attempt\":1}]"));
        assertCleanedWithoutF2(rec);
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(1);
        assertThat(finalAttempt(r.id())).isEqualTo(1);
    }

    // #9
    @Test
    void aFactWithoutEvidenceIdsIsRemovedAndTheScenarioContinues() throws Exception {
        scriptScenario(fx("SC-NOEV"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertThat(rec.get("guardReports")).isEqualTo(jsonOf("[{\"outcome\":\"PASS_WITH_REMOVALS\",\"violations\":[{\"type\":\"FACT_WITHOUT_EVIDENCE\","
            + "\"claimId\":\"F2\",\"detail\":\"F2 cites no Evidence ID\",\"action\":\"REMOVED\"}],\"attempt\":1}]"));
        assertCleanedWithoutF2(rec);
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(1);
    }

    // #10
    @Test
    void aFailingGuardTriggersOneRegenerationWithTheViolationsAttached() throws Exception {
        scriptScenario(fx("SC-BAD"), fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        List<StubResponses.Request> gen = requests(GEN);
        assertThat(gen).hasSize(2);
        String t2 = gen.get(1).inputText();
        assertThat(t2).contains("Reason: GUARD_REGENERATION");
        assertThat(t2).contains("Your previous scenario failed the Evidence Guard. Return a new complete scenario without the problems listed in guard-violations.");
        assertThat(List.of(StubResponses.dataBlock(t2, "guard-violations").split("\n"))).containsExactlyElementsOf(G4_LINES);
        assertThat(instructionsOf(gen.get(1))).isEqualTo(instructionsOf(gen.get(0)));
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("attempt")).isEqualTo(2);
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertThat(rec.get("guardReports")).isEqualTo(jsonOf("[{\"outcome\":\"FAIL\",\"violations\":[" + F1_E099 + "," + F2_NOEV + ","
            + I1_REMOVED + "," + CHAIN_NO_FACT + "],\"attempt\":1},{\"outcome\":\"PASS\",\"violations\":[],\"attempt\":2}]"));
        assertThat(attemptReasons(r.id())).containsExactly("INITIAL", "GUARD_REGENERATION");
        assertThat(finalAttempt(r.id())).isEqualTo(2);
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(2);
    }

    // #11
    @Test
    void aSecondGuardFailureRejectsTheScenario() throws Exception {
        scriptScenario(fx("SC-BAD"), fx("SC-BAD"));
        Ran r = runV4(A);
        assertFailed(r.run(), "SCENARIO_REJECTED", REJECTED, "CONSTRUCTING_SCENARIO", 9);
        assertThat(requests(GEN)).hasSize(2);
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("attempt")).isEqualTo(2);
        assertThat(rec.get("accepted")).isEqualTo(false);
        List<Map<String, Object>> reports = list(rec.get("guardReports"));
        assertThat(reports).hasSize(2);
        assertThat(reports.get(0).get("outcome")).isEqualTo("FAIL");
        assertThat(reports.get(1).get("outcome")).isEqualTo("FAIL");
        List<Map<String, Object>> violations = list(reports.get(1).get("violations"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).allSatisfy(v -> assertThat(v.get("action")).isEqualTo("REJECTED"));
        assertThat(list(reports.get(0).get("violations"))).extracting(v -> v.get("action")).contains("REGENERATION_REQUESTED");
        assertThat(counts(r.run()).get("sourcesUsed")).isEqualTo(0);
        assertThat(finalAttempt(r.id())).isNull();
        assertThat(startRun(r.sid(), B).andReturn().getResponse().getStatus()).as("slot released").isEqualTo(202);
    }

    // #12
    @Test
    void aRegenerationThatIsInvalidGetsTheOneSchemaCorrectionWithBothBlocks() throws Exception {
        scriptScenario(fx("SC-BAD"), text("not json"), fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        assertThat(requests(GEN)).hasSize(3);
        assertThat(attemptReasons(r.id())).containsExactly("INITIAL", "GUARD_REGENERATION", "SCHEMA_CORRECTION");
        String t3 = requests(GEN).get(2).inputText();
        assertThat(t3).contains("Reason: SCHEMA_CORRECTION");
        assertThat(t3).contains("Your previous scenario failed the Evidence Guard.");
        assertThat(t3).contains("Your previous answer was invalid. Fix the errors listed in schema-errors and return the complete scenario again.");
        assertThat(List.of(StubResponses.dataBlock(t3, "guard-violations").split("\n"))).containsExactlyElementsOf(G4_LINES);
        assertThat(StubResponses.dataBlock(t3, "schema-errors")).isEqualTo("output is not valid JSON");
        assertThat(t3.indexOf("name=\"guard-violations\"")).isLessThan(t3.indexOf("name=\"schema-errors\""));
        assertThat(t3.lastIndexOf("<<<ORACUL_UNTRUSTED_DATA name=\"schema-errors\">>>"))
            .as("schema-errors is the last block").isEqualTo(t3.lastIndexOf("<<<ORACUL_UNTRUSTED_DATA"));
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("attempt")).isEqualTo(3);
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertThat(finalAttempt(r.id())).isEqualTo(3);
        List<String> outcomes = new ArrayList<>();
        for (Map<String, Object> g : list(rec.get("guardReports"))) outcomes.add((String) g.get("outcome") + "@" + g.get("attempt"));
        assertThat(outcomes).containsExactly("FAIL@1", "PASS@3");
    }

    // #14
    @Test
    void aFutureEventOnTheCutoffDateFailsTheGuard() throws Exception {
        scriptScenario(computed(input -> ScenarioFixtures.scV4(ScenarioFixtures.cutoffDate(input))), fx("SC-V4"));
        Ran r = runV4(A);
        assertCompleted(r.run());
        assertThat(requests(GEN)).hasSize(2);
        Map<String, Object> rec = structured(r);
        List<Map<String, Object>> reports = list(rec.get("guardReports"));
        assertThat(reports).hasSize(2);
        assertThat(reports.get(0).get("outcome")).isEqualTo("FAIL");
        assertThat(reports.get(0).get("attempt")).isEqualTo(1);
        List<Map<String, Object>> violations = list(reports.get(0).get("violations"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).get("type")).isEqualTo("FUTURE_EVENT_NOT_IN_HORIZON");
        assertThat(violations.get(0).get("action")).isEqualTo("REGENERATION_REQUESTED");
        String cutoff = cutoffDate(r);
        assertThat((String) violations.get(0).get("detail")).startsWith("futureEvent date " + cutoff + " must be after " + cutoff
            + " and no later than ");
        assertThat(reports.get(1).get("outcome")).isEqualTo("PASS");
        assertThat(rec.get("accepted")).isEqualTo(true);
    }
}
