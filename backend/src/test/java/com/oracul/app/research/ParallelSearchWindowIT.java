package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-52 range (c) / NFR-10 part 1: the search window {@code oracul.search.search-window} (PT5S here) cuts open requests and
 * keeps unsent ones from starting; both are FAILED, never EMPTY.
 *
 * <p>Deterministic by construction, no wall-clock race: the stub replies are held on latches the test controls. The
 * replies of "wave 1" (the first 8 requests) are released as soon as all 8 are in flight (about 0 s, far before the
 * window end); the replies of every later request are held until the test has the outcome (released in {@code finally}),
 * so they can only end by the window cut. The client timeout (30 s) and the rate-limit wait (30 s) are far longer than
 * the window, so only the window can end an open request or a 429 wait; the elapsed-time bounds below only tell
 * "ended by the window" from "ended by the timeout/wait" and keep a wide margin.
 */
// @trace FR-52, NFR-10
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=8",
    "oracul.news.google.timeout=PT30S",
    "oracul.search.search-window=PT5S",
    "oracul.news.google.rate-limit-wait=PT30S",
    "oracul.search.query-generation-window=PT2S",
})
class ParallelSearchWindowIT extends AbstractNewsSearchIT {

    private static final long WINDOW_MS = 5000;
    /** Far below the 30 s timeout / wait, far above the 5 s window: a call that returns before it was ended by the window. */
    private static final long NOT_BY_TIMEOUT_MS = 20_000;

    /** Replies held by the test: the first {@code releasedFirst} requests answer when all of them are in flight, the rest wait for {@link #gate}. */
    private final CountDownLatch gate = new CountDownLatch(1);

    private void holdAllButTheFirst(int releasedFirst) {
        CountDownLatch inFlight = new CountDownLatch(releasedFirst);
        AtomicInteger seen = new AtomicInteger();
        news.responder = req -> {
            try {
                if (seen.incrementAndGet() <= releasedFirst) {
                    inFlight.countDown();
                    inFlight.await(60, TimeUnit.SECONDS);
                } else {
                    gate.await(60, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StubNews.rss();
        };
    }

    @AfterEach
    void releaseHeldReplies() {
        gate.countDown();
    }

    @Test
    void theWindowCutsOpenRequestsAndStopsUnsentOnes(CapturedOutput out) throws Exception {
        holdAllButTheFirst(8);
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(20));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        gate.countDown();

        assertThat(news.requests).as("Q01-Q08 answered, Q09-Q16 sent and cut by the window, Q17-Q20 never sent").hasSize(16);
        List<SearchQuery> q = outcome.plan().getQueries();
        for (int i = 0; i < 20; i++) {
            assertThat(q.get(i).getStatus()).as("Q" + (i + 1))
                .isEqualTo(i < 8 ? SearchQueryStatus.EMPTY : SearchQueryStatus.FAILED);
        }
        assertThat(ms).as("the window cut the open requests (their replies were never released; the timeout is 30 s)")
            .isGreaterThanOrEqualTo(WINDOW_MS - 500).isLessThan(NOT_BY_TIMEOUT_MS);
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
        assertThat(news.requests).as("the 30 s wait would end after the window: no retry").hasSize(1);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(ms).as("FAILED at once, not after the wait and not at the window end").isLessThan(WINDOW_MS);
    }

    @Test
    void everyAnswerOutlastingTheWindowFailsEveryQueryNeverEmpty() throws Exception {
        // no answer is ever released before the outcome: the 8 open requests are cut, the other 16 queries are never sent; all are FAILED
        holdAllButTheFirst(0);
        var outcome = search(planOfSize(24));
        gate.countDown();
        assertThat(news.requests).hasSize(8);
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(outcome.allFailed()).isTrue();
    }
}
