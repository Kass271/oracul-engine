package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 25 of research-pipeline.md "Slice 06_events": concurrency 1 for both purposes still gives the same events. */
// @trace FR-14, FR-46
// news-search.md FR-46: F240 keeps 30 sources; batch sizes 5 / 3 keep 6 normalisation and 10 classification batches
@TestPropertySource(properties = {
    "oracul.events.normalization-batch-size=5",
    "oracul.events.classification-batch-size=3",
    "oracul.research.query-budget=18",
    "oracul.events.max-sources=1000",
    "oracul.events.normalization-concurrency=1",
    "oracul.events.classification-concurrency=1",
})
class EventConcurrencyOneIT extends AbstractEventIT {

    // #25 (16 sequential answers of 300 ms need more than the usual 10 s polling window)
    @Test
    void withConcurrencyOneNeverMoreThanOneBatchIsInFlight() throws Exception {
        gdeltF240();
        responses.delay(NORMALIZATION, Duration.ofMillis(300));
        responses.delay(CLASSIFICATION, Duration.ofMillis(300));
        Ran r = runWithin(A, 30_000);
        assertThat(requests(NORMALIZATION)).hasSize(6);
        assertThat(requests(CLASSIFICATION)).hasSize(10);
        assertThat(responses.maxInFlight(NORMALIZATION)).isEqualTo(1);
        assertThat(responses.maxInFlight(CLASSIFICATION)).isEqualTo(1);
        assertF240Events(r, 30); // identical to row 8
    }
}
