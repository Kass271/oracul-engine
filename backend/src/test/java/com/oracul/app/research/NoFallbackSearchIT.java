package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-03 FR-49 range (2), as changed by FR-52: there is no fallback provider. For every Google failure class {503, 429,
 * dropped connection, timeout, malformed XML, not started (guard)} x {the first query only, every query}: the failed
 * queries are FAILED, exactly one {@code /rss/search} request goes out per started query (two for a 429: the one retry
 * after rate-limit-wait), and the stub records no path starting {@code /api/v2/doc} (the path of the former provider) nor
 * any other route. A query the run guard did not let start cannot be "first only": for that class the two cases are "no
 * query starts" and "only query 1 starts" (the others are never started, so FAILED). Concurrency is 1 (harness default).
 */
// @trace FR-49, FR-52
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.timeout=PT1S",
    "oracul.news.google.rate-limit-wait=PT0S",
})
class NoFallbackSearchIT extends AbstractNewsSearchIT {

    private static final String FIRST_TEXT = "alpha1 beta1";

    enum Failure { HTTP_503, HTTP_429, DROPPED_CONNECTION, TIMEOUT, MALFORMED_XML, NOT_STARTED }

    static Stream<Arguments> grid() {
        List<Arguments> out = new ArrayList<>();
        for (Failure f : Failure.values()) {
            out.add(Arguments.of(f, false));
            out.add(Arguments.of(f, true));
        }
        return out.stream();
    }

    private static StubNews.Reply failingReply(Failure f) {
        return switch (f) {
            case HTTP_503 -> StubNews.status(503);
            case HTTP_429 -> new StubNews.Reply(429, "text/plain", "Too Many Requests", 0);
            case DROPPED_CONNECTION -> StubNews.drop();
            case TIMEOUT -> new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 1600);
            case MALFORMED_XML -> StubNews.rssBody("<rss version=\"2.0\"><channel><item><title>broken");
            case NOT_STARTED -> throw new IllegalArgumentException("never answered: the request is not started");
        };
    }

    /** A good answer: one item whose title carries the text of the requested query. */
    private StubNews.Reply good(StubNews.Request req) {
        return StubNews.rss(item("https://news.example.org/ok-" + req.number(), req.elements().get(0) + " story"));
    }

    @ParameterizedTest(name = "{0}, every query failing = {1}")
    @MethodSource("grid")
    void aFailedQueryIsFailedAfterItsRequestsAndNoOtherProviderIsAsked(Failure failure, boolean everyQuery, CapturedOutput out) throws Exception {
        var plan = planOfSize(8); // Q01...Q08, one request each
        boolean[] failed = new boolean[8];
        int perFailed = failure == Failure.HTTP_429 ? 2 : 1; // a 429 is retried once
        int started;
        SearchOutcomeHolder holder = new SearchOutcomeHolder();
        if (failure == Failure.NOT_STARTED) {
            BooleanSupplier guard = everyQuery ? () -> false : () -> news.requests.isEmpty();
            news.responder = this::good;
            holder.outcome = retrieval.search(plan, HorizonCode._1Y, guard);
            started = everyQuery ? 0 : 1;
            for (int i = 0; i < 8; i++) failed[i] = everyQuery || i > 0;
            assertThat(news.requests).as("exactly one request per started query").hasSize(started);
        } else {
            StubNews.Reply bad = failingReply(failure);
            news.responder = req -> everyQuery || req.elements().contains(FIRST_TEXT) ? bad : good(req);
            holder.outcome = search(plan);
            for (int i = 0; i < 8; i++) failed[i] = everyQuery || i == 0;
            int failedCount = everyQuery ? 8 : 1;
            started = failedCount * perFailed + (8 - failedCount);
            assertThat(news.requests).as("one request per query, two for a retried 429, no fallback").hasSize(started);
        }

        assertThat(news.paths).as("no path starting /api/v2/doc, no other route either").noneMatch(p -> p.startsWith("/api/v2/doc"))
            .allMatch(p -> p.equals("/rss/search"));
        assertThat(news.paths).hasSize(started);

        List<SearchQuery> q = holder.outcome.plan().getQueries();
        assertThat(q).hasSize(8);
        for (int i = 0; i < 8; i++) {
            if (failed[i]) {
                assertThat(q.get(i).getStatus()).as(q.get(i).getId()).isEqualTo(SearchQueryStatus.FAILED);
                assertThat(q.get(i).getArticlesReturned()).isEqualTo(0);
            } else {
                assertThat(q.get(i).getStatus()).as(q.get(i).getId() + " was answered").isIn(SearchQueryStatus.OK, SearchQueryStatus.EMPTY);
            }
        }
        assertThat(holder.outcome.allFailed()).isEqualTo(everyQuery);
        String log = out.getOut() + out.getErr();
        assertThat(log).doesNotContain("news group falling back").doesNotContain("news group failed");
    }

    /** Holds the outcome of either search call (lambda-friendly). */
    private static final class SearchOutcomeHolder {
        SourceRetrieval.SearchOutcome outcome;
    }
}
