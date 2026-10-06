package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 step 6 with real (scaled-down) time: Google spacing 0.3 s, google.timeout 1 s. The Google
 * requests are serial, start spaced and after the previous request ended, and the timeout boundary decides between an
 * answer and a FAILED group (phase-03 FR-49: no fallback provider).
 */
// @trace FR-48, FR-49
@TestPropertySource(properties = {
    "oracul.news.google.request-spacing=PT0.3S",
    "oracul.news.google.timeout=PT1S",
    "oracul.news.search-budget=PT60S",
})
class GoogleNewsTimingIT extends AbstractNewsSearchIT {

    private static final long TOLERANCE_MS = 70;
    private static final long ANSWER_MS = 150;

    private static long ms(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }

    @Test
    void googleRequestsAreSerialAndSpacedAndNoOtherRequestIsSent() throws Exception {
        news.responder = req -> new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", ANSWER_MS);
        var outcome = search(planOfSize(8));
        List<StubNews.Request> calls = news.requests.stream().sorted(java.util.Comparator.comparingLong(StubNews.Request::arrivedNanos)).toList();
        assertThat(calls).as("one request per group, nothing else").hasSize(4);
        assertThat(news.paths).containsOnly("/rss/search");
        for (int k = 0; k + 1 < calls.size(); k++) {
            long gap = ms(calls.get(k).arrivedNanos(), calls.get(k + 1).arrivedNanos());
            assertThat(gap).as("request " + (k + 2) + " starts after request " + (k + 1) + " ended").isGreaterThanOrEqualTo(ANSWER_MS - TOLERANCE_MS);
            assertThat(gap).as("Google starts are spaced").isGreaterThanOrEqualTo(300 - TOLERANCE_MS);
        }
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void anAnswerWithinTheGoogleTimeoutIsUsedAndASlowerOneFailsItsGroup() throws Exception {
        StubNews.Reply inTime = new StubNews.Reply(200, "application/rss+xml", StubNews.rss(StubNews.rssItem("alpha1 beta1 in time",
            news.baseUrl() + "/articles/g-in-time", StubNews.pubDate(java.time.Instant.now().minusSeconds(3600)), "Reuters",
            "https://www.reuters.com")).body(), 800); // within 1 s
        StubNews.Reply tooSlow = new StubNews.Reply(200, "application/rss+xml", StubNews.rss().body(), 1500); // beyond 1 s
        news.responder = req -> switch (req.number()) {
            case 1 -> inTime;
            case 2 -> tooSlow;
            default -> StubNews.rss();
        };
        var outcome = search(planOfSize(8));
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).as("answer at 0.8 s is used").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(0).getArticlesReturned()).isEqualTo(1);
        assertThat(news.requests).as("one request per group, no fallback for the slow one").hasSize(4);
        assertThat(news.paths).containsOnly("/rss/search");
        assertThat(q.subList(2, 4)).as("group 2 timed out: FAILED").allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(q.subList(4, 8)).allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
    }
}
