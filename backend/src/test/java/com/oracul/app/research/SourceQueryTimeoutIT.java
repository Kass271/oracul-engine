package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Provider timeout (oracul.news.google.timeout, the only per-request timeout) counts as FAILED, like any other provider error. */
// @trace FR-13, FR-44, FR-47, FR-50, FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=14",
    "oracul.news.google.timeout=PT0.5S",
    "oracul.news.google.concurrency=8", // FR-52: slow requests, 8 at a time (the assertions are counts only)
})
class SourceQueryTimeoutIT extends AbstractRunIT {

    /** An answer that arrives after 3 s, i.e. after the 0.5 s request timeout. */
    private static final StubNews.Reply SLOW =
        new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 3000);

    @Test
    @SuppressWarnings("unchecked")
    void oneSlowQueryFailsAndTheRunContinues() throws Exception {
        news.responder = req -> req.number() == 1
            ? SLOW
            : StubNews.rss(StubNews.rssItem("Title x" + req.number(), news.baseUrl() + "/rss/articles/x" + req.number(),
                StubNews.pubDate(java.time.Instant.now()), "reuters.com", "https://reuters.com"));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        // the query of the first request timed out; the other 5 of body A's 6 queries answer one article each (FR-52: one request per query)
        assertThat(news.requests).as("a timeout is not retried: one request per query").hasSize(6);
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status"))).count()).isEqualTo(1);
        assertThat(queries.stream().filter(q -> "OK".equals(q.get("status"))).count()).isEqualTo(5);
        assertThat(queries.stream().filter(q -> "EMPTY".equals(q.get("status"))).count()).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyQuerySlowStillCompletesWithTheNoEvidenceNote() throws Exception {
        news.responder = req -> SLOW;
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitRun(sid, id, 20_000, m -> "FAILED".equals(m.get("status")) || "COMPLETED".equals(m.get("status")));
        // run-control.md FR-47: every query FAILED is no failure any more
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).as("body B: one GENERAL pipeline with 3 queries").hasSize(3).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
        assertThat(news.requests).as("one request per planned query, none retried").hasSize(queries.size());
    }
}
