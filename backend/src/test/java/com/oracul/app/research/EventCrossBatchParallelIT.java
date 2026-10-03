package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Rows 26 and 27 (concurrency 2 variant) of research-pipeline.md "Slice 06_events": batch 2 is answered before batch 1;
 * the result must not depend on the completion order. Also reruns every test of {@link EventCrossBatchIT} at concurrency 2.
 */
// @trace FR-14
@TestPropertySource(properties = "oracul.events.normalization-concurrency=2")
class EventCrossBatchParallelIT extends EventCrossBatchIT {

    // #26
    @Test
    void theResultDoesNotDependOnWhichBatchAnswersFirst() throws Exception {
        responses.delayBatch(1, Duration.ofSeconds(1));
        Ran r = runThree(
            events(ev("[\"S001\",\"S002\"]", "null", "[\"WHO\"]", "WHO approves new pandemic vaccine", "null", 0.8)),
            events(ev("[\"S003\"]", "null", "[\"who\"]", "WHO approves pandemic vaccine", "null", 0.8)));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(responses.maxInFlight(NORMALIZATION)).as("both batches were in flight together").isEqualTo(2);
        StubResponses.Exchange batch1 = exchangeOfBatch(1);
        StubResponses.Exchange batch2 = exchangeOfBatch(2);
        assertThat(batch2.arrivedNanos - batch1.completedNanos).as("batch 2 arrives before batch 1 is answered").isNegative();
        assertThat(batch2.completedNanos - batch1.completedNanos).as("batch 2 is answered first").isNegative();
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("id")).isEqualTo("EV001");
        assertThat(events.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(events.get(0).get("entities")).isEqualTo(List.of("WHO"));
        assertThat(events.get(0).get("summary")).isEqualTo("WHO approves new pandemic vaccine");
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(1);
    }

    // #27 with concurrency 2 and batch 2 answering first
    @Test
    void theSummaryRuleIsReappliedAfterTheMergeWhenBatchOneAnswersLast() throws Exception {
        assertMergeRestatesTheDisagreement(true);
    }

    private StubResponses.Exchange exchangeOfBatch(int k) throws InterruptedException {
        StubResponses.Exchange x = responses.exchanges.stream()
            .filter(e -> AbstractEventIT.NORMALIZATION.equals(e.purpose) && StubResponses.batch(e.request) == k)
            .findFirst().orElseThrow(() -> new AssertionError("no request of batch " + k));
        for (int i = 0; i < 100 && x.completedNanos == 0; i++) Thread.sleep(10); // completion is stamped right after the reply
        return x;
    }
}
