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
 * run. At RANKING the run decides an evidence note from the number of Evidence IDs of the pack and the thresholds of the realism
 * band (defaults 5 / 3 / 1), continues, and ends COMPLETED with a story. wildcard-evidence.md slice 06 (FR-57): every kept source
 * counts as core evidence; fixture N(n) = n distinct articles with the default classification (n <= 4 in one wildcard, 5 <= n <= 8
 * in two wildcards, sizes [4, n - 4]).
 */
// @trace FR-31, FR-47, FR-53, FR-57
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

    /** Run body: {@code wildcards} catalogue wildcards at intensity 5 with the given realism (FR-53 harness {@link #wildcardsBody}). */
    private static String bodyA(int wildcards, int realism) {
        return wildcardsBody(wildcards).replace("\"realism\":8", "\"realism\":" + realism);
    }

    /** Wildcards of fixture N(n): n <= 4 -> 1, 5 <= n <= 8 -> 2 (no empty group, nothing cut by the per-pipeline selection). */
    private static int wildcardsOf(int n) {
        return n <= 4 ? 1 : 2;
    }

    private static String bodyN(int n, int realism) {
        return bodyA(wildcardsOf(n), realism);
    }

    /**
     * Fixture N(n): n distinct articles served by {@code newsArticlesPerPipeline} (sizes [n] or [4, n - 4]) with the default
     * classification; n = 0 is the empty feed. The pack then has n Evidence IDs (every kept source), {@code core} / {@code
     * counterSignals} are empty and {@code eventsSelected} = n, {@code counterSignals} = 0.
     */
    private Ran runN(int n, String body) throws Exception {
        news.reset();
        if (n > 0) {
            List<Art> arts = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                arts.add(new Art("k-" + i, "reuters.com", "K article " + i));
                news.site("k-" + i, "Publisher " + i);
            }
            if (n <= 4) newsArticlesPerPipeline(arts, n);
            else newsArticlesPerPipeline(arts, 4, n - 4);
        }
        Ran r = run(body);
        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).as("core of N(" + n + ")").isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).isEmpty();
        assertThat(sectionIds(pack)).as("Evidence IDs of N(" + n + ")").hasSize(n);
        assertThat(counts(r.run()).get("eventsSelected")).as("eventsSelected of N(" + n + ")").isEqualTo(n);
        assertThat(counts(r.run()).get("counterSignals")).isEqualTo(0);
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

    private static Map<String, Object> note(Map<String, Object> run) {
        assertThat(run.get("evidenceNote")).as("evidenceNote of " + run).isNotNull();
        return (Map<String, Object>) run.get("evidenceNote");
    }

    // #1
    @Test
    void twoCoreItemsAtRealismTenCompleteWithTheInsufficientEvidenceNote() throws Exception {
        Ran r = runN(2, bodyN(2, 10));
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
        assertThat(counts(run).get("eventsSelected")).isEqualTo(2);
        assertThat(counts(run).get("counterSignals")).isEqualTo(0);

        assertWrittenFromThePack();
        assertThat(attemptRows(r.id())).isGreaterThanOrEqualTo(1);
        assertThat(storyRows(r.id())).isEqualTo(1);
        assertThat(section(pack(r), "core")).isEmpty();
        assertThat(section(pack(r), "counterSignals")).isEmpty();
        assertThat(sectionIds(pack(r))).containsExactly("E001", "E002");

        assertThat(structured(r).get("accepted")).isEqualTo(true);
        assertThat(map(result(r).get("story")).get("headline")).isEqualTo("Stub headline from the future");
        assertThat(json(getRun(r.sid(), r.id()))).as("the note is part of every getRun answer").isEqualTo(run);

        // the active-run slot is released
        assertThat(startRun(r.sid(), bodyN(2, 8)).andReturn().getResponse().getStatus()).isEqualTo(202);
    }

    static Stream<Arguments> decisionTable() {
        // every realism 1-10 at the boundary classes n = needed - 1, needed, needed + 1 (default thresholds 5 / 3 / 1); n = 0 is NO_EVIDENCE
        return IntStream.rangeClosed(1, 10).boxed().flatMap(realism -> IntStream.rangeClosed(-1, 1)
            .mapToObj(delta -> Arguments.of(realism, needed(realism) + delta)));
    }

    // #2 (range & invariant of FR-47 / FR-59: the note decision, every realism 1-10; total = distinct Evidence IDs = core)
    @ParameterizedTest(name = "realism {0}, {1} sources")
    @MethodSource("decisionTable")
    void theNoteDecisionIsRightForEveryRealismAtTheThresholdBoundaries(int realism, int n) throws Exception {
        int needed = needed(realism);
        Ran r = runN(n, bodyN(n, realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(run.get("headline")).isNotNull();
        assertThat(requests(STORY)).hasSize(1);
        if (n == 0) {
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("kind", "NO_EVIDENCE");
            expected.put("message", NO_EVIDENCE_MESSAGE);
            expected.put("coreItems", 0);
            expected.put("coreNeeded", needed);
            assertThat(note(run)).isEqualTo(expected);
            assertThat(run.get("suggestedRealism")).as("NO_EVIDENCE has no suggestion").isNull();
            assertThat(requests(GEN).get(0).inputText()).contains("\n" + SPECULATIVE_TASK + "\n");
            return;
        }
        if (n < needed) {
            Map<String, Object> expected = new LinkedHashMap<>();
            expected.put("kind", "INSUFFICIENT_EVIDENCE");
            expected.put("message", insufficientMessage(realism, n, needed));
            expected.put("coreItems", n);
            expected.put("coreNeeded", needed);
            assertThat(note(run)).isEqualTo(expected);
            assertThat(run.get("suggestedRealism")).as("max(1, realism - 2)").isEqualTo(Math.max(1, realism - 2));
        } else {
            assertThat(run.get("evidenceNote")).as("the evidence meets the realism: no note").isNull();
            assertThat(run.get("suggestedRealism")).isNull();
        }
        assertThat(requests(GEN).get(0).inputText()).doesNotContain(SPECULATIVE_TASK);
    }

    static Stream<Arguments> suggestions() {
        // realism 3 -> 1, 2 -> 1 and "realism 1: note without suggestion" cannot happen with thresholds 5 / 3 / 1 and at least one
        // source: they are asserted in the pure EvidenceNotesTest
        return Stream.of(Arguments.of(10, 8), Arguments.of(9, 7));
    }

    // #2: the suggestion classes of the spec
    @ParameterizedTest(name = "realism {0} -> suggestedRealism {1}")
    @MethodSource("suggestions")
    void suggestedRealismIsTwoLowerButAtLeastOne(int realism, int suggested) throws Exception {
        Ran r = runN(2, bodyN(2, realism));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(note(r.run()).get("kind")).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(r.run().get("suggestedRealism")).isEqualTo(suggested);
    }

    // message classes: item / items
    @ParameterizedTest(name = "realism {0}, core {1}")
    @ValueSource(strings = {"7,1", "9,4", "10,2"})
    void theMessageSaysItemForOneAndItemsOtherwise(String pair) throws Exception {
        int realism = Integer.parseInt(pair.split(",")[0]);
        int core = Integer.parseInt(pair.split(",")[1]);
        Ran r = runN(core, bodyN(core, realism));
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
        news.reset();
        Ran r = run(bodyA(1, realism));
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
        assertThat(sectionIds(pack)).isEmpty();
        assertThat(requests(GEN).get(0).inputText()).contains("\n" + SPECULATIVE_TASK + "\n");
        assertThat(requests(STORY)).hasSize(1);
        assertThat(list(result(r).get("sources"))).as("the SOURCES panel is empty").isEmpty();
    }

    // alternative runs reuse the parent's pack and copy its note and suggestedRealism
    @Test
    void anAlternativeOfAnInsufficientRunCopiesTheNoteAndTheSuggestion() throws Exception {
        Ran p = runN(2, bodyN(2, 10));
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
        Ran r = runN(2, bodyN(2, 10));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        String raw = getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("Exception", "com.oracul", "{\"core");
        assertThat(raw).contains(insufficientMessage(10, 2, 5).replace("'", "'"));
        assertThat((String) note(r.run()).get("message")).doesNotContain("http");
    }
}
