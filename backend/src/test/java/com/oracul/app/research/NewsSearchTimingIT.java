package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44 steps 4-6 with real (scaled-down) time: spacing 0.4 s, rate-limit-wait 0.5 s,
 * query-timeout 1 s. Serial requests, starts at least request-spacing apart and never before the previous one ended,
 * the timeout boundary, exactly one retry after a 429, no retry for any other failure.
 */
// @trace FR-44
@TestPropertySource(properties = {
    "oracul.news.request-spacing=PT0.4S",
    "oracul.news.rate-limit-wait=PT0.5S",
    "oracul.news.query-timeout=PT1S",
    "oracul.news.search-budget=PT60S",
    "oracul.news.max-requests=4",
})
class NewsSearchTimingIT extends AbstractNewsSearchIT {

    private static final long SPACING_MS = 400;
    private static final long TOLERANCE_MS = 60;

    private long gapMs(int a, int b) {
        return (gdelt.requests.get(b).arrivedNanos() - gdelt.requests.get(a).arrivedNanos()) / 1_000_000;
    }

    @Test
    void startsAreAtLeastTheRequestSpacingApartEvenWhenAnswersAreFast() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 100);
        search(planOfSize(8));
        assertThat(gdelt.requests).hasSize(4);
        for (int k = 0; k + 1 < gdelt.requests.size(); k++) {
            assertThat(gapMs(k, k + 1)).as("start(" + (k + 2) + ") - start(" + (k + 1) + ")").isGreaterThanOrEqualTo(SPACING_MS - TOLERANCE_MS);
        }
    }

    @Test
    void aRequestNeverStartsBeforeThePreviousOneHasEnded() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 700); // longer than the spacing
        search(planOfSize(8));
        assertThat(gdelt.requests).hasSize(4);
        for (int k = 0; k + 1 < gdelt.requests.size(); k++) {
            assertThat(gapMs(k, k + 1)).as("serial: request " + (k + 2) + " waits for the answer of " + (k + 1))
                .isGreaterThanOrEqualTo(700 - TOLERANCE_MS);
        }
    }

    @Test
    void anAnswerWithinTheTimeoutIsUsedAndASlowerOneFailsItsGroup() throws Exception {
        gdelt.responder = req -> switch (req.number()) {
            case 1 -> new StubGdelt.Reply(200, "application/json",
                StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/in-time", "in time"))), 800); // within 1 s
            case 2 -> new StubGdelt.Reply(200, "application/json",
                StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/too-late", "too late"))), 1500); // beyond 1 s
            default -> StubGdelt.json("{}");
        };
        var outcome = search(planOfSize(8));
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).as("answer at 0.8 s is used").isEqualTo(SearchQueryStatus.OK);
        assertThat(q.get(0).getArticlesReturned()).isEqualTo(1);
        assertThat(q.get(1).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(q.get(2).getStatus()).as("answer after the timeout: the group FAILED").isEqualTo(SearchQueryStatus.FAILED);
        assertThat(q.get(3).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(q.get(2).getArticlesReturned()).isEqualTo(0);
        assertThat(q.get(4).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(gdelt.requests).as("a timeout is not retried").hasSize(4);
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void aRateLimitedGroupIsRetriedOnceAfterTheRateLimitWait() throws Exception {
        gdelt.responder = req -> req.number() == 1
            ? new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds.", 0) : StubGdelt.json("{}");
        var outcome = search(planOfSize(8));
        assertThat(gdelt.requests).as("4 groups + exactly 1 retry").hasSize(5);
        assertThat(gdelt.requests.get(1).params()).as("identical request").isEqualTo(gdelt.requests.get(0).params());
        assertThat(gdelt.requests.get(1).elements()).isEqualTo(gdelt.requests.get(0).elements());
        assertThat(gapMs(0, 1)).as("retry >= rate-limit-wait after the 429 answer").isGreaterThanOrEqualTo(500 - TOLERANCE_MS);
        assertThat(gdelt.requests.get(2).elements()).as("then the next group").isNotEqualTo(gdelt.requests.get(0).elements());
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.EMPTY));
    }

    @Test
    void aRateLimitedGroupThatAnswersAfterTheRetryIsUsed() throws Exception {
        gdelt.responder = req -> req.number() == 1
            ? new StubGdelt.Reply(429, "text/plain", "slow down", 0)
            : req.number() == 2
                ? StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/after-retry", "after retry"))))
                : StubGdelt.json("{}");
        var outcome = search(planOfSize(8));
        assertThat(gdelt.requests).hasSize(5);
        assertThat(outcome.plan().getQueries().get(0).getStatus()).isEqualTo(SearchQueryStatus.OK);
        assertThat(outcome.plan().getQueries().get(0).getArticlesReturned()).isEqualTo(1);
        assertThat(outcome.articlesRetrieved()).isEqualTo(1);
    }

    @Test
    void aSecondRateLimitFailsTheGroupAfterExactlyTwoRequests() throws Exception {
        gdelt.responder = req -> req.number() <= 2
            ? new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds.", 0) : StubGdelt.json("{}");
        var outcome = search(planOfSize(8));
        assertThat(gdelt.requests).as("2 for group 1, then one each for groups 2-4").hasSize(5);
        assertThat(gdelt.requests.get(2).elements()).isNotEqualTo(gdelt.requests.get(0).elements());
        List<SearchQuery> q = outcome.plan().getQueries();
        assertThat(q.get(0).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(q.get(1).getStatus()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(q.get(2).getStatus()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(outcome.allFailed()).isFalse();
    }

    @Test
    void rateLimitedTwiceOnEveryGroupFailsEveryGroup() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds.", 0);
        var outcome = search(planOfSize(12)); // 4 groups of 3
        assertThat(gdelt.requests).as("4 groups x (first attempt + one retry)").hasSize(8);
        assertThat(outcome.allFailed()).isTrue();
    }

    static Stream<Arguments> noRetryFailures() {
        return Stream.of(
            Arguments.of("500", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.status(500)),
            Arguments.of("503", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.status(503)),
            Arguments.of("404", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.status(404)),
            Arguments.of("connection dropped", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.drop()),
            Arguments.of("200 invalid JSON", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.json("{not json")),
            Arguments.of("200 text/plain", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r ->
                new StubGdelt.Reply(200, "text/plain", "Your search contained a phrase that is too short", 0)),
            Arguments.of("timeout", (java.util.function.Function<StubGdelt.Request, StubGdelt.Reply>) r ->
                new StubGdelt.Reply(200, "application/json", "{}", 1500)));
    }

    @ParameterizedTest(name = "{0}: failed after exactly one request")
    @MethodSource("noRetryFailures")
    void nothingButA429IsRetried(String name, java.util.function.Function<StubGdelt.Request, StubGdelt.Reply> reply) throws Exception {
        gdelt.responder = reply;
        var outcome = search(planOfSize(8));
        assertThat(gdelt.requests).as(name).hasSize(4);
        assertThat(outcome.plan().getQueries()).allSatisfy(q -> assertThat(q.getStatus()).isEqualTo(SearchQueryStatus.FAILED));
        assertThat(outcome.allFailed()).isTrue();
    }
}
