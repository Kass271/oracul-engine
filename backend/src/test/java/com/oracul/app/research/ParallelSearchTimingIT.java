package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQueryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * wildcard-search.md FR-52 (slice 03): the parallelism of the dispatcher, with a google.timeout well above the 2 s
 * answer delay (a shorter timeout would time the first eight queries out and free their permits early).
 */
// @trace FR-52
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=8",
    "oracul.news.google.timeout=PT10S",
})
class ParallelSearchTimingIT extends AbstractNewsSearchIT {

    // 9 queries x 2 s: eight open at once, the ninth waits for a permit; serial would take 18 s
    @Test
    void nineQueriesWithATwoSecondAnswerAreAllAnsweredInUnderSixSecondsWithEightOpen() throws Exception {
        news.responder = StubNews.slow(2000, req -> StubNews.rss());
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(9));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(news.requests).hasSize(9);
        assertThat(news.maxOpen()).as("8 permits: eight open at once, never more").isEqualTo(8);
        assertThat(ms).as("serial would take 18 s").isLessThan(6000);
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
    }
}
