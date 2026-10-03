package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 22 of research-pipeline.md "Slice 06_events": the default source cap (120) on F240 (205 sources, all tied on quality and date). */
// @trace FR-14
@TestPropertySource(properties = "oracul.research.query-budget=18")
class EventSourceCapIT extends AbstractEventIT {

    // #22
    @Test
    void onlyTheFirst120SourcesInIdOrderAreNormalizedWhenEverythingTies() throws Exception {
        gdeltF240();
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(counts(r.run()).get("articlesConsidered")).as("unchanged: every stored source").isEqualTo(205);
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(120);

        assertThat(requests(NORMALIZATION)).hasSize(3);
        Map<Integer, String> byBatch = normalizationInputsByBatch();
        assertThat(byBatch.keySet()).containsExactly(1, 2, 3);
        for (int k = 1; k <= 3; k++) {
            List<String> ids = StubResponses.sourceIds(byBatch.get(k));
            assertThat(ids).as("batch " + k).hasSize(40);
            assertThat(ids.get(0)).isEqualTo(String.format("S%03d", (k - 1) * 40 + 1));
            assertThat(ids.get(39)).isEqualTo(String.format("S%03d", k * 40));
            assertThat(byBatch.get(k)).contains("Batch: " + k + " of 3 | Sources: 40");
        }
        assertThat(requests(CLASSIFICATION)).hasSize(6);

        assertF240Events(r, 120);

        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources).as("sources that were not selected stay listed").hasSize(205);
        for (Map<String, Object> s : sources) {
            int n = Integer.parseInt(((String) s.get("id")).substring(1));
            if (n <= 120) {
                assertThat(s.get("entities")).as((String) s.get("id")).isEqualTo(List.of("Entity " + s.get("id")));
            } else {
                assertThat(s.get("entities")).as((String) s.get("id") + " was not selected").isEqualTo(List.of());
            }
        }
    }
}
