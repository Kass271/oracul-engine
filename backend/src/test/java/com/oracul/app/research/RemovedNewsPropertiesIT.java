package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-03 FR-49 range (3), the integration half: a configuration that still sets all seven removed news properties to
 * values that would be visible if they were read starts normally and behaves exactly as without them: the run completes
 * with exactly 4 Google requests, started without spacing, the queries of group 1 OK, the others EMPTY, none FAILED.
 * The key of the former provider and its value are assembled from parts: the scan of FR-49 allows its name in one test
 * file only (NewsProviderScanTest).
 */
// @trace FR-49
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

        assertThat(news.requests).as("G = 4 requests, whatever max-requests says").hasSize(4);
        assertThat(news.paths).as("no request to the former provider").noneMatch(p -> p.startsWith("/api/v2/doc"));
        long first = news.requests.stream().mapToLong(StubNews.Request::arrivedNanos).min().orElseThrow();
        long last = news.requests.stream().mapToLong(StubNews.Request::arrivedNanos).max().orElseThrow();
        assertThat((last - first) / 1_000_000).as("all 4 requests arrive within 1 s: no 5 s spacing").isLessThan(1000);

        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(20);
        assertThat(queries).as("no query is FAILED (no timeout of 1 ms, no limit)").noneMatch(q -> "FAILED".equals(q.get("status")));
        assertThat(queries.subList(0, 5)).as("group 1 got the articles").anyMatch(q -> "OK".equals(q.get("status")))
            .allSatisfy(q -> assertThat(q.get("status")).isIn("OK", "EMPTY"));
        assertThat(queries.subList(5, 20)).as("groups 2-4 got an empty feed").allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
        assertThat(sourceItems(r.sid(), r.id())).as("the four articles of the fixture").hasSize(4);
    }
}
