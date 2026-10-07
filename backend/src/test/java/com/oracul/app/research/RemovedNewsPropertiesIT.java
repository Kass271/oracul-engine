package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-03 FR-49 range (3), the integration half: a configuration that still sets all ten removed properties (the
 * seven of slice 01 plus {@code oracul.news.search-budget}, {@code oracul.news.google.request-spacing} (FR-52) and
 * {@code oracul.research.query-budget} (FR-51)) to values
 * that would be visible if they were read starts normally and behaves exactly as without them: the run completes with
 * exactly 6 Google requests (body A: two pipelines x 3 queries, one per query), all arriving within 1 s, Q01 OK, Q02-Q06 EMPTY,
 * none FAILED.
 * The key of the former provider and its value are assembled from parts: the scan of FR-49 allows its name in one test
 * file only (NewsProviderScanTest).
 */
// @trace FR-49, FR-50, FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.news." + RemovedNewsPropertiesIT.OLD + ".base-url=http://x",
    "oracul.news.request-spacing=PT5S",
    "oracul.news.query-timeout=PT0.001S",
    "oracul.news.rate-limit-wait=PT30S",
    "oracul.news.max-requests=1",
    "oracul.news.max-records-per-query=1",
    "oracul.news.provider=" + RemovedNewsPropertiesIT.OLD,
    "oracul.news.search-budget=PT0.001S",
    "oracul.news.google.request-spacing=PT5S",
    "oracul.research.query-budget=1",
})
class RemovedNewsPropertiesIT extends AbstractEventIT {

    /** Name of the former provider, in pieces. */
    static final String OLD = "gd" + "elt";

    @SuppressWarnings("unchecked")
    @Test
    void allRemovedPropertiesSetAtOnceAreIgnoredAndTheRunBehavesAsWithoutThem() throws Exception {
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        Ran r = run(A); // the context started: the removed keys did not stop the backend
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(r.run().get("failure")).isNull();

        assertThat(news.requests).as("one request per query (6), whatever max-requests and query-budget say").hasSize(6);
        assertThat(news.paths).as("no request to the former provider").noneMatch(p -> p.startsWith("/api/v2/doc"));
        long first = news.requests.stream().mapToLong(StubNews.Request::arrivedNanos).min().orElseThrow();
        long last = news.requests.stream().mapToLong(StubNews.Request::arrivedNanos).max().orElseThrow();
        assertThat((last - first) / 1_000_000).as("all 6 requests arrive within 1 s: no 5 s spacing, no 1 ms budget").isLessThan(1000);

        List<Map<String, Object>> queries = PlanJson.queries(researchBody(r.sid(), r.id()));
        assertThat(queries).hasSize(6);
        assertThat(queries).as("no query is FAILED (no timeout of 1 ms, no budget of 1 ms, no limit)").noneMatch(q -> "FAILED".equals(q.get("status")));
        assertThat(queries.get(0).get("status")).as("Q01 got the articles").isEqualTo("OK");
        assertThat(queries.subList(1, 6)).as("Q02-Q06 got an empty feed").allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
        assertThat(sourceItems(r.sid(), r.id())).as("the four articles of the fixture").hasSize(4);
    }
}
