package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 25 of research-pipeline.md "Slice 06_events": concurrency 1 for both purposes still gives the same events. */
// @trace FR-14
@TestPropertySource(properties = {
    "oracul.research.query-budget=18",
    "oracul.events.max-sources=1000",
    "oracul.events.normalization-concurrency=1",
    "oracul.events.classification-concurrency=1",
})
class EventConcurrencyOneIT extends AbstractEventIT {

    // #25 (17 sequential answers of 300 ms need more than the usual 10 s polling window)
    @Test
    void withConcurrencyOneNeverMoreThanOneBatchIsInFlight() throws Exception {
        gdeltF240();
        responses.delay(NORMALIZATION, Duration.ofMillis(300));
        responses.delay(CLASSIFICATION, Duration.ofMillis(300));
        Ran r = runWithin(A, 30_000);
        assertThat(requests(NORMALIZATION)).hasSize(6);
        assertThat(requests(CLASSIFICATION)).hasSize(11);
        assertThat(responses.maxInFlight(NORMALIZATION)).isEqualTo(1);
        assertThat(responses.maxInFlight(CLASSIFICATION)).isEqualTo(1);
        assertF240Events(r, 205); // identical to row 8
    }
}
