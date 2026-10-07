package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 24 of research-pipeline.md "Slice 06_events": the configured concurrency bounds the batches in flight, the result does not change. */
// @trace FR-14, FR-46, FR-53
// FR-46 / FR-53: F240_BODY_TEN keeps 30 sources; batch sizes 5 / 3 keep 6 normalisation and 10 classification batches
@TestPropertySource(properties = {
    "oracul.events.normalization-batch-size=5",
    "oracul.events.classification-batch-size=3",
    "oracul.research.query-budget=18",
    "oracul.events.max-sources=1000",
    "oracul.events.normalization-concurrency=2",
    "oracul.events.classification-concurrency=3",
})
class EventConcurrencyIT extends AbstractEventIT {

    // #24
    @Test
    void batchesRunInParallelUpToTheConfiguredConcurrencyAndNotBeyond() throws Exception {
        newsF240();
        responses.delay(NORMALIZATION, Duration.ofMillis(300));
        responses.delay(CLASSIFICATION, Duration.ofMillis(300));
        Ran r = run(F240_BODY_TEN);
        assertThat(requests(NORMALIZATION)).hasSize(6);
        assertThat(requests(CLASSIFICATION)).hasSize(10);
        assertThat(responses.maxInFlight(NORMALIZATION)).isEqualTo(2);
        assertThat(responses.maxInFlight(CLASSIFICATION)).isEqualTo(3);
        assertF240Events(r, 30); // identical to row 8
    }
}
