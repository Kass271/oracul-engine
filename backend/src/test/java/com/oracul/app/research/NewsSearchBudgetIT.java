package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44 "search-budget": no request starts at or after the budget end and every request's timeout
 * is cut to the budget end. The spec example scaled by 1/5: budget 2.4 s, spacing 1 s, every answer takes 1 s.
 */
// @trace FR-44
@TestPropertySource(properties = {
    "oracul.news.request-spacing=PT1S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.query-timeout=PT10S",
    "oracul.news.search-budget=PT2.4S",
    "oracul.news.max-requests=4",
})
class NewsSearchBudgetIT extends AbstractNewsSearchIT {

    @Test
    void noRequestStartsAfterTheBudgetAndTheLastTimeoutIsCutToTheBudgetEnd() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 1000);
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(8));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        // request 1 at 0 s (answered), request 2 at 1 s (answered), request 3 at 2 s with its timeout cut to 0.4 s: FAILED
        // at 2.4 s; request 4 is not sent
        assertThat(gdelt.requests).hasSize(3);
        List<SearchQuery> q = outcome.plan().getQueries();
        for (int i = 0; i < 4; i++) {
            assertThat(q.get(i).getStatus()).as("groups 1 and 2 (queries 1-4) were answered: query index " + i).isEqualTo(SearchQueryStatus.EMPTY);
        }
        for (int i = 4; i < 8; i++) {
            assertThat(q.get(i).getStatus()).as("group 3 cut at the budget end, group 4 never sent: query index " + i).isEqualTo(SearchQueryStatus.FAILED);
        }
        assertThat(q.get(7).getArticlesReturned()).isEqualTo(0);
        assertThat(elapsedMs).as("the third request is cut at the budget end (2.4 s), not answered at 3 s").isLessThan(2900);
        assertThat(outcome.allFailed()).as("groups 1 and 2 were answered").isFalse();
    }

    @Test
    void aRateLimitRetryThatWouldStartAfterTheBudgetIsNotSent() throws Exception {
        // every request is answered 429 at once; the retry of group 1 waits 0 s but must obey the spacing (1 s): it starts at 1 s;
        // group 2 would start at 2 s (its retry at 3 s is past the budget end 2.4 s)
        gdelt.responder = req -> new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds.", 0);
        var outcome = search(planOfSize(8));
        assertThat(gdelt.requests).as("starts at 0 s, 1 s (retry of group 1) and 2 s (group 2); its retry would start at 3 s").hasSize(3);
        assertThat(outcome.allFailed()).isTrue();
    }
}
