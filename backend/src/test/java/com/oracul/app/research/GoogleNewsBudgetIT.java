package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 step 6: the search budget covers both providers. Budget 2 s, every request takes 0.7 s:
 * Google 1 (0 s, fails), GDELT 1 (0.7 s, answered), Google 2 (1.4 s, its timeout cut to the 0.6 s left: FAILED at 2.0 s);
 * its GDELT fallback and groups 3 and 4 are never sent.
 */
// @trace FR-48
@TestPropertySource(properties = {
    "oracul.news.request-spacing=PT0S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.google.timeout=PT10S",
    "oracul.news.query-timeout=PT10S",
    "oracul.news.search-budget=PT2S",
    "oracul.news.max-requests=4",
})
class GoogleNewsBudgetIT extends AbstractNewsSearchIT {

    @Test
    void noRequestOfEitherProviderStartsAfterTheBudgetAndTheLastTimeoutIsCutToTheBudgetEnd() throws Exception {
        gdelt.rssResponder = req -> new StubGdelt.Reply(503, "text/plain", "", 700);
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 700);
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(8));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertThat(gdelt.rssRequests).as("Google 1 and Google 2; groups 3 and 4 are past the budget").hasSize(2);
        assertThat(gdelt.requests).as("the fallback of group 1; the fallback of group 2 would start at the budget end").hasSize(1);
        for (var r : gdelt.rssRequests) assertThat((r.arrivedNanos() - t0) / 1_000_000).isLessThan(2000);
        for (var r : gdelt.requests) assertThat((r.arrivedNanos() - t0) / 1_000_000).isLessThan(2000);
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.subList(0, 2)).allSatisfy(x -> assertThat(x.getStatus()).as("group 1 answered by GDELT").isEqualTo(SearchQueryStatus.EMPTY));
        assertThat(q.subList(2, 8)).allSatisfy(x -> assertThat(x.getStatus()).as("groups 2-4 got no answer").isEqualTo(SearchQueryStatus.FAILED));
        assertThat(elapsedMs).as("the second Google request is cut at the budget end (2.0 s), not answered at 2.1 s").isLessThan(2500);
        assertThat(outcome.allFailed()).isFalse();
    }
}
