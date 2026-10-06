package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 step 6: the search budget covers the Google requests. Budget 2 s, every request takes 0.7 s:
 * Google 1 (0 s), Google 2 (0.7 s), Google 3 (1.4 s, its timeout cut to the 0.6 s left: FAILED at 2.0 s); group 4 is never
 * sent. There is no fallback provider (phase-03 FR-49).
 */
// @trace FR-48, FR-49
@TestPropertySource(properties = {
    "oracul.news.google.timeout=PT10S",
    "oracul.news.search-budget=PT2S",
})
class GoogleNewsBudgetIT extends AbstractNewsSearchIT {

    @Test
    void noRequestStartsAfterTheBudgetAndTheLastTimeoutIsCutToTheBudgetEnd() throws Exception {
        news.responder = req -> new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 700);
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(8));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(news.requests).as("Google 1, 2 and 3; group 4 is past the budget").hasSize(3);
        assertThat(news.paths).as("no other provider").containsOnly("/rss/search");
        for (var r : news.requests) assertThat((r.arrivedNanos() - t0) / 1_000_000).isLessThan(2000);
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.subList(0, 4)).allSatisfy(x -> assertThat(x.getStatus()).as("groups 1-2 answered (empty feed)").isEqualTo(SearchQueryStatus.EMPTY));
        assertThat(q.subList(4, 8)).allSatisfy(x -> assertThat(x.getStatus()).as("groups 3-4 got no answer").isEqualTo(SearchQueryStatus.FAILED));
        assertThat(elapsedMs).as("the third request is cut at the budget end (2.0 s), not answered at 2.1 s").isLessThan(2500);
        assertThat(outcome.allFailed()).isFalse();
    }
}
