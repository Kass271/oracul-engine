package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-52 range (c) / NFR-10 part 1: the search window {@code oracul.search.search-window} (PT5S here) cuts open requests and
 * keeps unsent ones from starting; both are FAILED, never EMPTY.
 *
 * <p>Timing is sized so that scheduler jitter under a loaded full-suite run (2 Gradle forks) cannot move a request over
 * the window edge: window W = 5 s, wave delay d = 3.3 s (about 2/3 W). Wave 1 starts at 0 s, wave 2 at d = 3.3 s (1.7 s
 * before the window end), wave 3 would start at 2d = 6.6 s (1.6 s after it). The rate-limit wait is pinned to 10 s
 * (longer than the window) so a 429 is never retried; the 429 case is bounded far below that wait.
 */
// @trace FR-52, NFR-10
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=8",
    "oracul.news.google.timeout=PT10S",
    "oracul.search.search-window=PT5S",
    "oracul.news.google.rate-limit-wait=PT10S",
    "oracul.search.query-generation-window=PT2S",
})
class ParallelSearchWindowIT extends AbstractNewsSearchIT {

    private static final long WINDOW_MS = 5000;
    private static final long WAVE_MS = 3300;

    @Test
    void theWindowCutsOpenRequestsAndStopsUnsentOnes(CapturedOutput out) throws Exception {
        news.responder = StubNews.slow(WAVE_MS, req -> StubNews.rss());
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(20));
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(news.requests).as("Q01-Q08 at 0 s, Q09-Q16 at 3.3 s, Q17-Q20 are never sent").hasSize(16);
        List<SearchQuery> q = outcome.plan().getQueries();
        for (int i = 0; i < 20; i++) {
            assertThat(q.get(i).getStatus()).as("Q" + (i + 1))
                .isEqualTo(i < 8 ? SearchQueryStatus.EMPTY : SearchQueryStatus.FAILED);
        }
        assertThat(ms).as("the call returns at the window end, not after the reply of the cut wave (6.6 s)").isLessThan(WINDOW_MS + 1000);
        String log = out.getOut() + out.getErr();
        assertThat(log.split("google news request skipped: search window ended", -1).length - 1)
            .as("one line per request that was never sent").isEqualTo(4);
        assertThat(log).doesNotContain("alpha").doesNotContain("when:");
    }

    @Test
    void aRateLimitWhoseWaitWouldEndAfterTheWindowIsFailedAtOnce() throws Exception {
        news.responder = req -> StubNews.tooMany();
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(1));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(news.requests).as("the 10 s wait would end after the window: no retry").hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(ms).as("FAILED at once, not after the wait").isLessThan(3000);
    }

    @Test
    void everyAnswerOutlastingTheWindowFailsEveryQueryNeverEmpty() throws Exception {
        // every answer outlasts the window: the 8 open requests are cut, the other 16 queries are never sent; all are FAILED
        news.responder = StubNews.slow(WINDOW_MS + 1500, req -> StubNews.rss());
        var outcome = search(planOfSize(24));
        assertThat(news.requests).hasSize(8);
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(outcome.allFailed()).isTrue();
    }
}
