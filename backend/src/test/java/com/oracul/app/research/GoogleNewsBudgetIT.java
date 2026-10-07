package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 step 6 as changed by FR-52 / NFR-10: the search window {@code oracul.search.search-window}
 * covers the Google requests. Window 2 s, concurrency 1, every request takes 0.7 s: Q01 (0 s), Q02 (0.7 s), Q03 (1.4 s, its
 * timeout cut to the 0.6 s left: FAILED at 2.0 s); Q04 onwards are never sent. There is no fallback provider (phase-03 FR-49).
 */
// @trace FR-48, FR-49, FR-52
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=1",
    "oracul.news.google.timeout=PT10S",
    "oracul.search.search-window=PT2S",
    "oracul.search.query-generation-window=PT2S",
})
class GoogleNewsBudgetIT extends AbstractNewsSearchIT {

    @Test
    void noRequestStartsAfterTheWindowAndTheLastTimeoutIsCutToTheWindowEnd() throws Exception {
        news.responder = req -> new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 700);
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(8));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(news.requests).as("Q01, Q02 and Q03; Q04 onwards are past the window").hasSize(3);
        assertThat(news.paths).as("no other provider").containsOnly("/rss/search");
        for (var r : news.requests) assertThat((r.arrivedNanos() - t0) / 1_000_000).isLessThan(2000);
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.subList(0, 2)).allSatisfy(x -> assertThat(x.getStatus()).as("Q01-Q02 answered (empty feed)").isEqualTo(SearchQueryStatus.EMPTY));
        assertThat(q.subList(2, 8)).allSatisfy(x -> assertThat(x.getStatus()).as("Q03 was cut, Q04-Q08 got no request: FAILED, never EMPTY").isEqualTo(SearchQueryStatus.FAILED));
        assertThat(elapsedMs).as("the third request is cut at the window end (2.0 s), not answered at 2.1 s").isLessThan(2500);
        assertThat(outcome.allFailed()).isFalse();
    }
}
