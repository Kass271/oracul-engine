package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 step 6 with real (scaled-down) time: Google spacing 0.3 s, GDELT spacing 0.5 s, google.timeout
 * 1 s. One request at a time across both providers, Google starts spaced and after the previous request ended, GDELT starts
 * spaced, GDELT only after a failed Google request of the same group, the Google timeout boundary.
 */
// @trace FR-48
@TestPropertySource(properties = {
    "oracul.news.google.request-spacing=PT0.3S",
    "oracul.news.request-spacing=PT0.5S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.google.timeout=PT1S",
    "oracul.news.query-timeout=PT2S",
    "oracul.news.search-budget=PT60S",
    "oracul.news.max-requests=4",
})
class GoogleNewsTimingIT extends AbstractNewsSearchIT {

    private static final long TOLERANCE_MS = 70;
    private static final long ANSWER_MS = 150;

    /** One request of either provider: provider, arrival time. */
    private record Call(String provider, long nanos) {}

    private List<Call> calls() {
        List<Call> out = new ArrayList<>();
        gdelt.rssRequests.forEach(r -> out.add(new Call("G", r.arrivedNanos())));
        gdelt.requests.forEach(r -> out.add(new Call("D", r.arrivedNanos())));
        out.sort(Comparator.comparingLong(Call::nanos));
        return out;
    }

    private static long ms(long fromNanos, long toNanos) {
        return (toNanos - fromNanos) / 1_000_000;
    }

    @Test
    void requestsAreSerialSpacedPerProviderAndGdeltFollowsAFailedGoogleRequestOfTheSameGroup() throws Exception {
        gdelt.rssResponder = req -> new StubGdelt.Reply(503, "text/plain", "", ANSWER_MS);
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", ANSWER_MS);
        var outcome = search(planOfSize(8));
        List<Call> calls = calls();
        assertThat(calls.stream().map(Call::provider).toList()).as("per group Google, then its GDELT fallback")
            .containsExactly("G", "D", "G", "D", "G", "D", "G", "D");
        for (int k = 0; k + 1 < calls.size(); k++) {
            assertThat(ms(calls.get(k).nanos(), calls.get(k + 1).nanos())).as("request " + (k + 2) + " starts after request " + (k + 1) + " ended")
                .isGreaterThanOrEqualTo(ANSWER_MS - TOLERANCE_MS);
        }
        List<Call> google = calls.stream().filter(c -> c.provider().equals("G")).toList();
        List<Call> gd = calls.stream().filter(c -> c.provider().equals("D")).toList();
        for (int k = 0; k + 1 < google.size(); k++) {
            assertThat(ms(google.get(k).nanos(), google.get(k + 1).nanos())).as("Google starts are spaced")
                .isGreaterThanOrEqualTo(300 - TOLERANCE_MS);
        }
        for (int k = 0; k + 1 < gd.size(); k++) {
            assertThat(ms(gd.get(k).nanos(), gd.get(k + 1).nanos())).as("GDELT starts are spaced by request-spacing")
                .isGreaterThanOrEqualTo(500 - TOLERANCE_MS);
        }
        assertThat(gdelt.requests.get(0).elements()).isEqualTo(gdelt.rssRequests.get(0).elements());
        assertThat(gdelt.requests.get(3).elements()).isEqualTo(gdelt.rssRequests.get(3).elements());
        assertThat(outcome.allFailed()).as("GDELT answered every group").isFalse();
    }

    @Test
    void anAnswerWithinTheGoogleTimeoutIsUsedAndASlowerOneFailsItsGroupOverToGdelt() throws Exception {
        gdelt.rssResponder = req -> switch (req.number()) {
            case 1 -> new StubGdelt.Reply(200, "application/rss+xml", StubGdelt.rss(StubGdelt.rssItem("alpha1 beta1 in time",
                gdelt.baseUrl() + "/articles/g-in-time", StubGdelt.pubDate(java.time.Instant.now().minusSeconds(3600)), "Reuters",
                "https://www.reuters.com")).body(), 800); // within 1 s
            case 2 -> new StubGdelt.Reply(200, "application/rss+xml", StubGdelt.rss().body(), 1500); // beyond 1 s
            default -> StubGdelt.rss();
        };
        gdelt.responder = req -> StubGdelt.json("{}");
        var outcome = search(planOfSize(8));
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).as("answer at 0.8 s is used").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(0).getArticlesReturned()).isEqualTo(1);
        assertThat(gdelt.rssRequests).hasSize(4);
        assertThat(gdelt.requests).as("only group 2 (Google timed out) went to GDELT").hasSize(1);
        assertThat(gdelt.requests.get(0).elements()).isEqualTo(gdelt.rssRequests.get(1).elements());
        assertThat(q.get(2).getStatus()).as("GDELT answered group 2 (empty)").isEqualTo(SearchQueryStatus.EMPTY);
    }
}
