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
 * phase-03 FR-49 range (2): there is no fallback provider. For every Google failure class {503, 429, dropped connection,
 * timeout, malformed XML, not started (guard / budget)} x {the first group only, every group}: the queries of the failed
 * group are FAILED, exactly one {@code /rss/search} request goes out per started group, and the stub records no path
 * starting {@code /api/v2/doc} (the path of the former provider) nor any other route.
 * A group the run guard did not let start cannot be "first only": for that class the two cases are "no group starts" and
 * "only group 1 starts" (groups 2-4 are never started, so FAILED).
 */
// @trace FR-49
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.google.timeout=PT1S",
})
class NoFallbackSearchIT extends AbstractNewsSearchIT {

    private static final String FIRST_ELEMENT = "alpha1 beta1";

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

    /** A good answer: one item whose title carries the first element of the requested group. */
    private StubNews.Reply good(StubNews.Request req) {
        return StubNews.rss(item("https://news.example.org/ok-" + req.number(), req.elements().get(0) + " story"));
    }

    @ParameterizedTest(name = "{0}, every group failing = {1}")
    @MethodSource("grid")
    void aFailedGroupIsFailedAfterExactlyOneRequestAndNoOtherProviderIsAsked(Failure failure, boolean everyGroup, CapturedOutput out) throws Exception {
        var plan = planOfSize(8); // 4 groups of 2: Q01-Q02, Q03-Q04, Q05-Q06, Q07-Q08
        boolean[] failed = new boolean[4];
        int started;
        SearchOutcomeHolder holder = new SearchOutcomeHolder();
        if (failure == Failure.NOT_STARTED) {
            BooleanSupplier guard = everyGroup ? () -> false : () -> news.requests.isEmpty();
            news.responder = this::good;
            holder.outcome = retrieval.search(plan, HorizonCode._1Y, guard);
            started = everyGroup ? 0 : 1;
            for (int g = 0; g < 4; g++) failed[g] = everyGroup || g > 0;
        } else {
            StubNews.Reply bad = failingReply(failure);
            news.responder = req -> everyGroup || req.elements().contains(FIRST_ELEMENT) ? bad : good(req);
            holder.outcome = search(plan);
            started = 4;
            for (int g = 0; g < 4; g++) failed[g] = everyGroup || g == 0;
        }

        assertThat(news.requests).as("exactly one /rss/search request per started group (no retry, no fallback)").hasSize(started);
        assertThat(news.requests.stream().map(StubNews.Request::elements).distinct().count()).as("each started group once").isEqualTo(started);
        assertThat(news.paths).as("no path starting /api/v2/doc, no other route either").noneMatch(p -> p.startsWith("/api/v2/doc"))
            .allMatch(p -> p.equals("/rss/search"));
        assertThat(news.paths).hasSize(started);

        List<SearchQuery> q = holder.outcome.plan().getQueries();
        assertThat(q).hasSize(8);
        for (int g = 0; g < 4; g++) {
            for (int i = 2 * g; i < 2 * g + 2; i++) {
                if (failed[g]) {
                    assertThat(q.get(i).getStatus()).as("group " + (g + 1) + " " + q.get(i).getId()).isEqualTo(SearchQueryStatus.FAILED);
                    assertThat(q.get(i).getArticlesReturned()).isEqualTo(0);
                } else {
                    assertThat(q.get(i).getStatus()).as("group " + (g + 1) + " was answered").isIn(SearchQueryStatus.OK, SearchQueryStatus.EMPTY);
                }
            }
        }
        boolean allFailed = everyGroup;
        assertThat(holder.outcome.allFailed()).isEqualTo(allFailed);
        String log = out.getOut() + out.getErr();
        assertThat(log).doesNotContain("news group falling back");
        if (failure != Failure.NOT_STARTED) {
            long failedGroups = java.util.stream.IntStream.range(0, 4).filter(g -> failed[g]).count();
            assertThat(log.split("news group failed", -1).length - 1).as("one WARN per failed group").isEqualTo((int) failedGroups);
        }
    }

    /** Holds the outcome of either search call (lambda-friendly). */
    private static final class SearchOutcomeHolder {
        SourceRetrieval.SearchOutcome outcome;
    }
}
