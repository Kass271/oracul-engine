package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Rows 6-7 and 27 of research-pipeline.md "Slice 06_events": several normalisation batches and the cross-batch merge.
 * Scripted answers are keyed by the "Batch: k of n" line, never by arrival order (batches run in parallel).
 * {@link EventCrossBatchSerialIT} and {@link EventCrossBatchParallelIT} rerun every test with another concurrency.
 */
// @trace FR-14
@TestPropertySource(properties = "oracul.events.normalization-batch-size=2")
class EventCrossBatchIT extends AbstractEventIT {

    static String ev(String ids, String date, String entities, String summary, String disagreement, double confidence) {
        return "{\"sourceIds\":" + ids + ",\"date\":" + date + ",\"category\":\"health\",\"entities\":" + entities
            + ",\"summary\":\"" + summary + "\",\"disagreement\":" + disagreement + ",\"confidence\":" + confidence + "}";
    }

    static String events(String... evs) {
        return "{\"events\":[" + String.join(",", evs) + "]}";
    }

    Ran runThree(String batch1, String batch2) throws Exception {
        gdeltArticles(v4(3));
        scriptBatch(1, batch1);
        scriptBatch(2, batch2);
        return run(A);
    }

    // #6
    @Test
    void similarEventsOfDifferentBatchesAreMerged() throws Exception {
        Ran r = runThree(
            events(ev("[\"S001\",\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8)),
            events(ev("[\"S003\"]", "null", "[\"who\"]", "WHO approves pandemic vaccine", "null", 0.8)));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(NORMALIZATION)).hasSize(2);
        Map<Integer, String> byBatch = normalizationInputsByBatch();
        assertThat(byBatch.get(1)).contains("Batch: 1 of 2 | Sources: 2");
        assertThat(byBatch.get(2)).contains("Batch: 2 of 2 | Sources: 1");
        assertThat(StubResponses.sourceIds(byBatch.get(1))).containsExactly("S001", "S002");
        assertThat(StubResponses.sourceIds(byBatch.get(2))).containsExactly("S003");
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("id")).isEqualTo("EV001");
        assertThat(events.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(events.get(0).get("entities")).isEqualTo(List.of("WHO"));
        assertThat(events.get(0).get("summary")).isEqualTo("WHO approves new pandemic vaccine");
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(1);
        assertThat(source(r, "S003").get("entities")).isEqualTo(List.of("WHO"));
    }

    // #7
    @Test
    void dissimilarSummariesOfDifferentBatchesStaySeparate() throws Exception {
        Ran r = runThree(
            events(ev("[\"S001\",\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8)),
            events(ev("[\"S003\"]", "null", "[\"WHO\"]", "Dock workers strike over humanoid robots", "null", 0.8)));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002"));
        assertThat(events.get(1).get("sourceIds")).isEqualTo(List.of("S003"));
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(2);
    }

    // merge needs a shared entity
    @Test
    void similarSummariesWithoutASharedEntityAreNotMerged() throws Exception {
        Ran r = runThree(
            events(ev("[\"S001\",\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8)),
            events(ev("[\"S003\"]", "null", "[\"EMA\"]", "WHO approves pandemic vaccine", "null", 0.8)));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(events(r)).hasSize(2);
    }

    // merge fields: union, earliest date, max confidence, disagreement of the later one when the earlier has none (R1: summary states it)
    @Test
    void mergedEventCombinesEntitiesDateConfidenceAndDisagreement() throws Exception {
        Ran r = runThree(
            events(ev("[\"S001\",\"S002\"]", "\"2026-10-02\"", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.6)),
            events(ev("[\"S003\"]", "\"2026-10-01\"", "[\"who\",\"Extra Org\"]", "WHO approves pandemic vaccine", "\"the price\"", 0.9)));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(1);
        Map<String, Object> e = events.get(0);
        assertThat(e.get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(e.get("entities")).isEqualTo(List.of("WHO", "Extra Org"));
        assertThat(e.get("date")).isEqualTo("2026-10-01");
        assertThat(num(e.get("confidence"))).isEqualTo(0.9);
        assertThat(e.get("disagreement")).isEqualTo("the price");
        assertThat((String) e.get("summary")).as("R1: a merged event with a disagreement states it").contains("Reports differ on the price");
    }

    // events of the same batch are never merged
    @Test
    void eventsOfTheSameBatchAreNeverMerged() throws Exception {
        gdeltArticles(v4(3));
        scriptBatch(1, events(ev("[\"S001\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8),
            ev("[\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8)));
        scriptBatch(2, events(ev("[\"S003\"]", "null", "[\"Dock workers\"]", "Dock workers strike", "null", 0.8)));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(events(r)).hasSize(3);
    }

    // each batch is validated on its own: the second batch is retried with its own errors only
    @Test
    void anInvalidBatchIsRetriedAlone() throws Exception {
        gdeltArticles(v4(3));
        scriptBatch(1, events(ev("[\"S001\",\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8)));
        scriptBatch(2, events(ev("[\"S777\"]", "null", "[\"who\"]", "WHO approves pandemic vaccine", "null", 0.8)),
            events(ev("[\"S003\"]", "null", "[\"who\"]", "WHO approves pandemic vaccine", "null", 0.8)));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<StubResponses.Request> calls = requests(NORMALIZATION);
        assertThat(calls).hasSize(3);
        List<StubResponses.Request> batch2 = calls.stream().filter(q -> StubResponses.batch(q) == 2).toList();
        assertThat(batch2).hasSize(2);
        assertThat(calls.stream().filter(q -> StubResponses.batch(q) == 1)).hasSize(1);
        assertThat(batch2.get(1).inputText()).contains("Batch: 2 of 2 | Sources: 1");
        assertThat(lines(StubResponses.dataBlock(batch2.get(1).inputText(), "validation-errors")))
            .containsExactly("unknown source id S777", "source id S003 is missing");
        assertThat(events(r)).hasSize(1);
    }

    // #27 (R1): the summary rule is applied again after a merge; shared by the concurrency variants
    void assertMergeRestatesTheDisagreement(boolean batch1AnswersLast) throws Exception {
        if (batch1AnswersLast) responses.delayBatch(1, Duration.ofSeconds(1));
        Ran r = runThree(
            events(ev("[\"S001\",\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine.", "null", 0.8)),
            events(ev("[\"S003\"]", "null", "[\"who\"]", "WHO approves pandemic vaccine", "\"the price\"", 0.8)));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("disagreement")).isEqualTo("the price");
        assertThat(events.get(0).get("summary")).isEqualTo("WHO approves new pandemic vaccine. Reports differ on the price.");
        assertThat(events.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
    }

    // #27 (concurrency of this class's configuration, no artificial delay)
    @Test
    void theSummaryRuleIsReappliedAfterTheMerge() throws Exception {
        assertMergeRestatesTheDisagreement(false);
    }
}
