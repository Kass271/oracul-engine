package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Row 22 of research-pipeline.md "Slice 06_events": the normaliser source cap on F240. Since news-search.md FR-46 a run keeps
 * at most 30 sources, so the default cap (120) can never bite; the cap is exercised with oracul.events.max-sources=12
 * (batches of 5 sources / 5 events keep the multi-batch layout).
 */
// @trace FR-14, FR-46
@TestPropertySource(properties = {
    "oracul.research.query-budget=18",
    "oracul.events.max-sources=12",
    "oracul.events.normalization-batch-size=5",
    "oracul.events.classification-batch-size=5",
})
class EventSourceCapIT extends AbstractEventIT {

    // #22
    @Test
    void onlyTheFirst12SourcesInIdOrderAreNormalizedWhenEverythingTies() throws Exception {
        gdeltF240();
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(counts(r.run()).get("articlesConsidered")).as("FR-46: at most 30 sources are kept").isEqualTo(30);
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(12);

        assertThat(requests(NORMALIZATION)).hasSize(3);
        Map<Integer, String> byBatch = normalizationInputsByBatch();
        assertThat(byBatch.keySet()).containsExactly(1, 2, 3);
        int[] sizes = {5, 5, 2};
        int next = 1;
        for (int k = 1; k <= 3; k++) {
            List<String> ids = StubResponses.sourceIds(byBatch.get(k));
            assertThat(ids).as("batch " + k).hasSize(sizes[k - 1]);
            assertThat(ids.get(0)).isEqualTo(String.format("S%03d", next));
            assertThat(ids.get(ids.size() - 1)).isEqualTo(String.format("S%03d", next + sizes[k - 1] - 1));
            assertThat(byBatch.get(k)).contains("Batch: " + k + " of 3 | Sources: " + sizes[k - 1]);
            next += sizes[k - 1];
        }
        assertThat(requests(CLASSIFICATION)).hasSize(3);

        assertF240Events(r, 12);

        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources).as("sources that were not selected stay listed").hasSize(30);
        for (Map<String, Object> s : sources) {
            int n = Integer.parseInt(((String) s.get("id")).substring(1));
            if (n <= 12) {
                assertThat(s.get("entities")).as((String) s.get("id")).isEqualTo(List.of("Entity " + s.get("id")));
            } else {
                assertThat(s.get("entities")).as((String) s.get("id") + " was not selected").isEqualTo(List.of());
            }
        }
    }
}
