package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Rows 8 and 13 of research-pipeline.md "Slice 06_events": F240 with default batch sizes (40 sources / 20 events), the
 * source cap lifted to 1000, default concurrency 4. Since news-search.md FR-46 the run keeps 30 of the 205 usable sources:
 * one normalisation batch of 30 sources, 30 events in two classification batches (20 + 10).
 */
// @trace FR-14, FR-15, FR-46
@TestPropertySource(properties = {"oracul.research.query-budget=18", "oracul.events.max-sources=1000"})
class EventBatchingIT extends AbstractEventIT {

    // #8 and #13
    @Test
    void thirtyKeptSourcesAreBatchedAndEveryClassificationIsInRange() throws Exception {
        gdeltF240();
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(30);
        assertThat(counts(r.run()).get("articlesConsidered")).isEqualTo(30);

        // arrival order of parallel batches is not defined: batches are identified by their "Batch: k of n" line
        List<StubResponses.Request> norm = requests(NORMALIZATION);
        assertThat(norm).hasSize(1);
        Map<Integer, String> byBatch = normalizationInputsByBatch();
        assertThat(byBatch.keySet()).containsExactly(1);
        String text = byBatch.get(1);
        assertThat(StubResponses.sourceIds(text)).hasSize(30);
        assertThat(text).contains("Batch: 1 of 1 | Sources: 30");
        assertThat(StubResponses.sourceIds(text).get(0)).isEqualTo("S001");
        assertThat(StubResponses.sourceIds(text).get(29)).isEqualTo("S030");

        List<StubResponses.Request> cls = requests(CLASSIFICATION);
        assertThat(cls).hasSize(2);
        List<String> byFirstEvent = inputsByBatch(CLASSIFICATION);
        assertThat(byFirstEvent.stream().map(t -> StubResponses.eventIds(t).size()).toList())
            .containsExactly(20, 10);
        assertThat(purposes().lastIndexOf(NORMALIZATION)).as("classification starts after every normalisation batch finished")
            .isLessThan(purposes().indexOf(CLASSIFICATION));

        // NFR-2: bounded parallelism, never more than the configured 4 in flight
        assertThat(responses.maxInFlight(NORMALIZATION)).isLessThanOrEqualTo(4);
        assertThat(responses.maxInFlight(CLASSIFICATION)).isLessThanOrEqualTo(4);

        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(30);
        for (int i = 0; i < events.size(); i++) {
            Map<String, Object> e = events.get(i);
            assertThat(e.get("id")).isEqualTo(String.format("EV%03d", i + 1));
            assertThat(e.get("sourceIds")).isEqualTo(List.of(String.format("S%03d", i + 1)));
            Map<String, Object> c = classification(e);
            assertThat(c).as((String) e.get("id")).isNotNull();
            assertThat(num(c.get("sentiment"))).isBetween(-1.0, 1.0);
            for (String k : List.of("risk", "opportunity", "impact", "novelty", "sourceQuality")) {
                assertThat(num(c.get(k))).as(e.get("id") + " " + k).isBetween(0.0, 1.0);
            }
            for (Map<String, Object> w : wildcardMatches(e)) assertThat(num(w.get("score"))).isBetween(0.0, 1.0);
            assertThat(wildcardMatches(e).stream().map(w -> w.get("key")).toList())
                .isEqualTo(List.of("biology-new-pandemic", "robotics-humanoid-boom"));
        }
    }
}
