package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * run-control.md FR-47 (replaces the phase-01 "Slice 12_insufficient-evidence" tests of FR-31): a lack of evidence never ends a
 * run. At RANKING the run decides an evidence note from the CORE items of the pack and the thresholds of the realism band
 * (defaults 5 / 3 / 1), continues, and ends COMPLETED with a story. Fixture K(c, s) = c CORE items plus s counter-signal
 * candidates under the dark body.
 */
// @trace FR-31, FR-47
@TestPropertySource(properties = {
    "oracul.evidence.min-core.high=5",
    "oracul.evidence.min-core.medium=3",
    "oracul.evidence.min-core.low=1",
})
class InsufficientEvidenceIT extends AbstractStoryIT {

    static final String SPECULATIVE_TASK = "The Evidence Pack is empty: no current news could be used. Write a fully speculative "
        + "scenario: factsUsed, inferences and counterSignalsConsidered are empty arrays, the causal chain has only SPECULATION "
        + "steps followed by the single FUTURE_EVENT, and no Evidence ID appears anywhere.";
    static final String NO_EVIDENCE_MESSAGE = "No current news could be used — this future is speculative, not grounded in evidence.";

    /** MinCoreThresholds with the defaults 9-10 -> 5, 6-8 -> 3, 1-5 -> 1. */
    static int needed(int realism) {
        return realism >= 9 ? 5 : realism >= 6 ? 3 : 1;
    }

    static String insufficientMessage(int realism, int core, int needed) {
        return "Realism " + realism + " couldn't be fully met: only " + core + " core evidence " + (core == 1 ? "item" : "items")
            + " (needs " + needed + "). This future is less grounded.";
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

    private void assertWrittenFromThePack() {
        assertThat(requests(GEN)).as("SCENARIO_GENERATION requests").isNotEmpty();
        assertThat(requests(CRITIC)).as("SCENARIO_CRITIC requests").isNotEmpty();
        assertThat(requests(STORY)).as("STORY_WRITING requests").hasSize(1);
        assertThat(requests(GEN).get(0).inputText()).as("normal mode: no speculative TASK line").doesNotContain(SPECULATIVE_TASK)
            .doesNotContain("The Evidence Pack is empty");
    }

    private static final String CRITIC = "SCENARIO_CRITIC";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> note(Map<String, Object> run) {
        assertThat(run.get("evidenceNote")).as("evidenceNote of " + run).isNotNull();
        return (Map<String, Object>) run.get("evidenceNote");
    }

    // #1
    @Test
    void twoCoreItemsAtRealismTenCompleteWithTheInsufficientEvidenceNote() throws Exception {
        Ran r = runK(2, 3, bodyA(10));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("stage")).isEqualTo("WRITING_STORY");
        assertThat(run.get("stageIndex")).isEqualTo(10);
        assertThat(run.get("failure")).as("a lack of evidence is no failure").isNull();
        assertThat(run.get("headline")).isEqualTo("Stub headline from the future");
        assertThat(run.get("completedAt")).isNotNull();
        Map<String, Object> expectedNote = new LinkedHashMap<>();
        expectedNote.put("kind", "INSUFFICIENT_EVIDENCE");
        expectedNote.put("message", "Realism 10 couldn't be fully met: only 2 core evidence items (needs 5). This future is less grounded.");
        expectedNote.put("coreItems", 2);
        expectedNote.put("coreNeeded", 5);
        assertThat(note(run)).isEqualTo(expectedNote);
        assertThat(run.get("suggestedRealism")).isEqualTo(8);
        assertThat(run.get("evidencePackId")).isNotNull();
        assertThat(counts(run).get("eventsSelected")).isEqualTo(5);
        assertThat(counts(run).get("counterSignals")).isEqualTo(3);

        assertWrittenFromThePack();
        assertThat(attemptRows(r.id())).isGreaterThanOrEqualTo(1);
        assertThat(storyRows(r.id())).isEqualTo(1);
        assertThat(section(pack(r), "core")).hasSize(2);
        assertThat(section(pack(r), "counterSignals")).hasSize(3);

        assertThat(structured(r).get("accepted")).isEqualTo(true);
        assertThat(map(result(r).get("story")).get("headline")).isEqualTo("Stub headline from the future");
        assertThat(json(getRun(r.sid(), r.id()))).as("the note is part of every getRun answer").isEqualTo(run);

        // the active-run slot is released
        assertThat(startRun(r.sid(), bodyA(8)).andReturn().getResponse().getStatus()).isEqualTo(202);
    }

    static Stream<Arguments> decisionTable() {
        // every realism 1-10 at the boundary classes core = needed - 1, needed, needed + 1 (default thresholds 5 / 3 / 1)
        return IntStream.rangeClosed(1, 10).boxed().flatMap(realism -> IntStream.rangeClosed(-1, 1)
            .mapToObj(delta -> Arguments.of(realism, needed(realism) + delta)));
    }

    // #2 (range & invariant of FR-47: the note decision, every realism 1-10)
    @ParameterizedTest(name = "realism {0}, core {1}")
    @MethodSource("decisionTable")
    void theNoteDecisionIsRightForEveryRealismAtTheThresholdBoundaries(int realism, int core) throws Exception {
        int needed = needed(realism);
        Ran r = runK(core, 3, bodyA(realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(run.get("headline")).isNotNull();
        assertThat(requests(STORY)).hasSize(1);
        if (core < needed) {
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("kind", "INSUFFICIENT_EVIDENCE");
            expected.put("message", insufficientMessage(realism, core, needed));
            expected.put("coreItems", core);
            expected.put("coreNeeded", needed);
            assertThat(note(run)).isEqualTo(expected);
            if (realism > 1) {
                assertThat(run.get("suggestedRealism")).as("max(1, realism - 2)").isEqualTo(Math.max(1, realism - 2));
            } else {
                assertThat(run.get("suggestedRealism")).as("realism 1 has no suggestion").isNull();
            }
        } else {
            assertThat(run.get("evidenceNote")).as("the evidence meets the realism: no note").isNull();
            assertThat(run.get("suggestedRealism")).isNull();
        }
        assertThat(requests(GEN).get(0).inputText()).doesNotContain(SPECULATIVE_TASK);
    }

    static Stream<Arguments> suggestions() {
        return Stream.of(Arguments.of(10, 8), Arguments.of(9, 7), Arguments.of(3, 1), Arguments.of(2, 1));
    }

    // #2: the suggestion classes of the spec
    @ParameterizedTest(name = "realism {0} -> suggestedRealism {1}")
    @MethodSource("suggestions")
    void suggestedRealismIsTwoLowerButAtLeastOne(int realism, int suggested) throws Exception {
        Ran r = runK(0, 3, bodyA(realism));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(note(r.run()).get("kind")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(r.run().get("suggestedRealism")).isEqualTo(suggested);
    }

    // #3
    @Test
    void realismOneHasANoteButNoSuggestedRealism() throws Exception {
        Ran r = runK(0, 3, bodyA(1));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(note(run).get("message")).isEqualTo(insufficientMessage(1, 0, 1));
        assertThat(run.get("suggestedRealism")).as("suggestedRealism present").isNull();
    }

    // message classes: item / items
    @ParameterizedTest(name = "realism {0}, core {1}")
    @ValueSource(strings = {"7,1", "3,0", "10,2"})
    void theMessageSaysItemForOneAndItemsOtherwise(String pair) throws Exception {
        int realism = Integer.parseInt(pair.split(",")[0]);
        int core = Integer.parseInt(pair.split(",")[1]);
        Ran r = runK(core, 3, bodyA(realism));
        String message = (String) note(r.run()).get("message");
        assertThat(message).isEqualTo(insufficientMessage(realism, core, needed(realism)));
        assertThat(message).contains(core == 1 ? "only 1 core evidence item (" : "core evidence items (");
        String raw = getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("Exception", "com.oracul", "{\"core");
    }

    // #4: an empty pack is NO_EVIDENCE for every realism (also with real thresholds), written up speculatively
    @ParameterizedTest(name = "empty pack at realism {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void anEmptyPackIsNoEvidenceForEveryRealism(int realism) throws Exception {
        gdelt.reset();
        Ran r = run(bodyA(realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(run.get("headline")).isNotNull();
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("kind", "NO_EVIDENCE");
        expected.put("message", NO_EVIDENCE_MESSAGE);
        expected.put("coreItems", 0);
        expected.put("coreNeeded", needed(realism));
        assertThat(note(run)).isEqualTo(expected);
        assertThat(run.get("suggestedRealism")).as("no suggestion for NO_EVIDENCE").isNull();
        assertThat(counts(run).get("eventsSelected")).isEqualTo(0);
        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).isEmpty();
        assertThat(requests(GEN)).hasSize(1);
        assertThat(requests(GEN).get(0).inputText()).contains("\n" + SPECULATIVE_TASK + "\n");
        assertThat(requests(STORY)).hasSize(1);
        assertThat(list(result(r).get("sources"))).as("the SOURCES panel is empty").isEmpty();
    }

    // alternative runs reuse the parent's pack and copy its note and suggestedRealism
    @Test
    void anAlternativeOfAnInsufficientRunCopiesTheNoteAndTheSuggestion() throws Exception {
        Ran p = runK(2, 3, bodyA(10));
        assertThat(p.run().get("status")).isEqualTo("COMPLETED");
        MvcResult res = mvc.perform(post("/api/runs/" + p.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(202);
        Map<String, Object> created = json(res.getResponse().getContentAsString());
        assertThat(created.get("evidenceNote")).as("copied at creation").isEqualTo(p.run().get("evidenceNote"));
        assertThat(created.get("suggestedRealism")).isEqualTo(8);
        Map<String, Object> done = awaitRun(p.sid(), (String) created.get("id"), 15_000,
            m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
        assertThat(done.get("status")).as("alternative: " + done).isEqualTo("COMPLETED");
        assertThat(done.get("evidenceNote")).isEqualTo(p.run().get("evidenceNote"));
        assertThat(done.get("suggestedRealism")).isEqualTo(8);
    }

    // #5
    @Test
    void theNoteMessageIsTheFixedTextOnly() throws Exception {
        Ran r = runK(2, 3, bodyA(10));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        String raw = getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("Exception", "com.oracul", "{\"core");
        assertThat(raw).contains(insufficientMessage(10, 2, 5).replace("'", "'"));
        assertThat((String) note(r.run()).get("message")).doesNotContain("http");
    }
}
