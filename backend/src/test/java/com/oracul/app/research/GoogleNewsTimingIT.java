package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 step 6 as changed by FR-52, with real (scaled-down) time: google.timeout 1 s. The
 * Google requests are no longer spaced and not serial — with 8 permits every request of 8 queries is open before the first
 * answer is in — and the timeout boundary decides between an answer and a FAILED query (phase-03 FR-49: no fallback).
 */
// @trace FR-48, FR-49, FR-52
@TestPropertySource(properties = {
    "oracul.news.google.concurrency=8",
    "oracul.news.google.timeout=PT1S",
})
class GoogleNewsTimingIT extends AbstractNewsSearchIT {

    private static final long ANSWER_MS = 400;

    @Test
    void googleRequestsStartTogetherWithoutSpacingAndNoOtherRequestIsSent() throws Exception {
        news.responder = req -> new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", ANSWER_MS);
        long t0 = System.nanoTime();
        var outcome = search(planOfSize(8));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        List<StubNews.Request> calls = news.requests.stream().sorted(java.util.Comparator.comparingLong(StubNews.Request::arrivedNanos)).toList();
        assertThat(calls).as("one request per query, nothing else").hasSize(8);
        assertThat(news.paths).containsOnly("/rss/search");
        long lastArrival = calls.get(7).arrivedNanos();
        long firstAnswer = news.finishedNanos.values().stream().mapToLong(Long::longValue).min().orElseThrow();
        assertThat(lastArrival).as("all 8 requests are open before the first answer is in").isLessThan(firstAnswer);
        assertThat((lastArrival - calls.get(0).arrivedNanos()) / 1_000_000).as("no spacing between starts").isLessThan(ANSWER_MS);
        assertThat(elapsedMs).as("serial would take 8 x 0.4 s").isLessThan(2000);
        assertThat(news.maxOpen()).isEqualTo(8);
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void anAnswerWithinTheGoogleTimeoutIsUsedAndASlowerOneFailsItsQuery() throws Exception {
        StubNews.Reply inTime = new StubNews.Reply(200, "application/rss+xml", StubNews.rss(StubNews.rssItem("alpha1 beta1 in time",
            news.baseUrl() + "/articles/g-in-time", StubNews.pubDate(java.time.Instant.now().minusSeconds(3600)), "Reuters",
            "https://www.reuters.com")).body(), 800); // within 1 s
        StubNews.Reply tooSlow = new StubNews.Reply(200, "application/rss+xml", StubNews.rss().body(), 1500); // beyond 1 s
        news.responder = req -> switch (req.elements().get(0)) {
            case "alpha1 beta1" -> inTime;
            case "alpha2 beta2" -> tooSlow;
            default -> StubNews.rss();
        };
        var outcome = search(planOfSize(8));
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).as("answer at 0.8 s is used").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(0).getArticlesReturned()).isEqualTo(1);
        assertThat(news.requests).as("one request per query, no fallback for the slow one").hasSize(8);
        assertThat(news.paths).containsOnly("/rss/search");
        assertThat(q.get(1).getStatus()).as("Q02 timed out: FAILED").isEqualTo(SearchQueryStatus.FAILED);
        assertThat(q.subList(2, 8)).allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
    }
}
