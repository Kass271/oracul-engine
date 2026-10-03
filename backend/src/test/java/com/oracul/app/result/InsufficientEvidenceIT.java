package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubGdelt;
import com.oracul.app.research.StubResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.TestPropertySource;

/**
 * generation-runs.md "Slice 12_insufficient-evidence" InsufficientEvidenceIT: the sufficiency check at the end of stage 6,
 * with fixture K(c, s) = c CORE items plus s counter-signal candidates under the dark body.
 */
// @trace FR-31
@TestPropertySource(properties = {
    "oracul.evidence.min-core.high=5",
    "oracul.evidence.min-core.medium=3",
    "oracul.evidence.min-core.low=1",
})
class InsufficientEvidenceIT extends AbstractStoryIT {

    private static final String CRITIC = "SCENARIO_CRITIC";

    private static String msg(int realism) {
        return "ORACUL found insufficient current evidence to construct this scenario at Realism " + realism + ".";
    }

    private static String bodyA(int realism) {
        return A.replace("\"realism\":8", "\"realism\":" + realism);
    }

    /** Fixture K(c, s): first GDELT request returns c + s articles, risky for EV001..EV<c>, opportunity for the rest. */
    private Ran runK(int c, int s, String body) throws Exception {
        gdelt.reset();
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= c + s; i++) {
            arts.add(new Art("k-" + i, "reuters.com", "K article " + i));
            gdelt.site("k-" + i, "Publisher " + i);
        }
        gdeltArticles(arts);
        always(CLASSIFICATION, req -> {
            List<String> entries = new ArrayList<>();
            for (String id : StubResponses.eventIds(req.inputText())) {
                String e = StubResponses.defaultClassificationEntry(id);
                boolean core = Integer.parseInt(id.substring(2)) <= c;
                e = e.replace("\"risk\":0.4", core ? "\"risk\":0.9" : "\"risk\":0.1")
                    .replace("\"opportunity\":0.6", core ? "\"opportunity\":0.1" : "\"opportunity\":0.8");
                entries.add(e);
            }
            return StubResponses.completed("{\"classifications\":[" + String.join(",", entries) + "]}");
        });
        Ran r = run(body);
        assertThat(section(pack(r), "core")).as("core size of K(" + c + "," + s + ")").hasSize(c);
        return r;
    }

    private void assertNoGeneration() {
        assertThat(requests(GEN)).as("SCENARIO_GENERATION requests").isEmpty();
        assertThat(requests(CRITIC)).as("SCENARIO_CRITIC requests").isEmpty();
        assertThat(requests(STORY)).as("STORY_WRITING requests").isEmpty();
    }

    // #1
    @Test
    void twoCoreItemsAtRealismTenEndInsufficientEvidence() throws Exception {
        Ran r = runK(2, 3, bodyA(10));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(run.get("stage")).isEqualTo("RANKING");
        assertThat(run.get("stageIndex")).isEqualTo(6);
        assertThat(run.get("stageLabel")).isEqualTo("Ranking evidence…");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"INSUFFICIENT_EVIDENCE\",\"message\":\"" + msg(10) + "\"}"));
        assertThat(run.get("suggestedRealism")).isEqualTo(8);
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(run.get("headline")).isNull();
        assertThat(run.get("hasOpenCriticIssues")).isEqualTo(false);
        assertThat(run.get("evidencePackId")).isNotNull();
        assertThat(counts(run).get("eventsSelected")).isEqualTo(5);
        assertThat(counts(run).get("counterSignals")).isEqualTo(3);
        assertThat(counts(run).get("sourcesUsed")).isEqualTo(0);

        assertNoGeneration();
        assertThat(attemptRows(r.id())).isZero();
        assertThat(storyRows(r.id())).isZero();

        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).hasSize(2);
        assertThat(section(pack, "counterSignals")).hasSize(3);

        assertScenarioNotReady(getStructured(r.sid(), r.id()));
        assertResultNotReady(getResult(r.sid(), r.id()));

        // the active-run slot is released
        assertThat(startRun(r.sid(), bodyA(8)).andReturn().getResponse().getStatus()).isEqualTo(202);
    }

    // #2
    @ParameterizedTest(name = "realism {0}, core {1} -> {2} {3}")
    @CsvSource({
        "10,4,INSUFFICIENT_EVIDENCE,8", "10,5,COMPLETED,0",
        "9,4,INSUFFICIENT_EVIDENCE,7", "9,5,COMPLETED,0",
        "8,2,INSUFFICIENT_EVIDENCE,6", "8,3,COMPLETED,0",
        "6,2,INSUFFICIENT_EVIDENCE,4", "6,3,COMPLETED,0",
        "5,0,INSUFFICIENT_EVIDENCE,3", "5,1,COMPLETED,0",
        "2,0,INSUFFICIENT_EVIDENCE,1"})
    void thresholdBoundariesPerRealismBand(int realism, int core, String expected, int suggested) throws Exception {
        Ran r = runK(core, 3, bodyA(realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo(expected);
        if ("INSUFFICIENT_EVIDENCE".equals(expected)) {
            assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"INSUFFICIENT_EVIDENCE\",\"message\":\"" + msg(realism) + "\"}"));
            assertThat(run.get("suggestedRealism")).isEqualTo(suggested);
            assertThat(run.get("stageIndex")).isEqualTo(6);
            assertThat(requests(GEN)).isEmpty();
        } else {
            assertThat(run.get("headline")).isNotNull();
            assertThat(run.get("suggestedRealism")).isNull();
            assertThat(run.get("failure")).isNull();
            assertThat(requests(STORY)).hasSize(1);
        }
    }

    // #3
    @Test
    void realismOneHasNoSuggestedRealism() throws Exception {
        Ran r = runK(0, 3, bodyA(1));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(((Map<?, ?>) run.get("failure")).get("message")).isEqualTo(msg(1));
        assertThat(run.containsKey("suggestedRealism")).as("suggestedRealism key present").isFalse();
    }

    // #4
    @Test
    void anEmptyPackEndsInsufficientEvidence() throws Exception {
        gdelt.reset();
        Ran r = run(A);
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(((Map<?, ?>) run.get("failure")).get("message")).isEqualTo(msg(8));
        assertThat(run.get("suggestedRealism")).isEqualTo(6);
        assertThat(run.get("stageIndex")).isEqualTo(6);
        assertThat(counts(run).get("eventsSelected")).isEqualTo(0);
        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).isEmpty();
        assertNoGeneration();
    }

    // #5
    @Test
    void theFailureMessageIsTheFixedTextOnly() throws Exception {
        Ran r = runK(2, 3, bodyA(10));
        assertThat(r.run().get("status")).isEqualTo("INSUFFICIENT_EVIDENCE");
        String raw = getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("http", "Exception", "com.oracul", "{\"core");
        assertThat(raw).contains(msg(10));
    }
}
