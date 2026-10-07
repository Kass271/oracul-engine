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
 * FR-52 range (c) / NFR-10 part 1: the search window {@code oracul.search.search-window} (PT2S here) cuts open requests and
 * keeps unsent ones from starting; both are FAILED, never EMPTY.
 */
// @trace FR-52, NFR-10
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=8",
    "oracul.news.google.timeout=PT10S",
    "oracul.search.search-window=PT2S",
    "oracul.search.query-generation-window=PT2S",
})
class ParallelSearchWindowIT extends AbstractNewsSearchIT {

    @Test
    void theWindowCutsOpenRequestsAndStopsUnsentOnes(CapturedOutput out) throws Exception {
        news.responder = StubNews.slow(1500, req -> StubNews.rss());
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(20));
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertThat(news.requests).as("Q01-Q08 at 0 s, Q09-Q16 at 1.5 s, Q17-Q20 are never sent").hasSize(16);
        List<SearchQuery> q = outcome.plan().getQueries();
        for (int i = 0; i < 20; i++) {
            assertThat(q.get(i).getStatus()).as("Q" + (i + 1))
                .isEqualTo(i < 8 ? SearchQueryStatus.EMPTY : SearchQueryStatus.FAILED);
        }
        assertThat(ms).as("the call returns at the window end, not after the 1.5 s of the cut requests").isLessThan(2500);
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
        assertThat(news.requests).as("the 2 s wait would end at the window end: no retry").hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(ms).as("FAILED at once, not after the wait").isLessThan(1500);
    }

    @Test
    void everyAnswerOutlastingTheWindowFailsEveryQueryNeverEmpty() throws Exception {
        // every answer outlasts the window: the 8 open requests are cut, the other 16 queries are never sent; all are FAILED
        news.responder = StubNews.slow(2500, req -> StubNews.rss());
        var outcome = search(planOfSize(24));
        assertThat(news.requests).hasSize(8);
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(outcome.allFailed()).isTrue();
    }
}
