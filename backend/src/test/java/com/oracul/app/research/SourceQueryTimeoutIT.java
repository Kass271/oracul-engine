package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Provider timeout (oracul.news.google.timeout, the only per-request timeout) counts as FAILED, like any other provider error. */
// @trace FR-13, FR-44, FR-47
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=14",
    "oracul.news.google.timeout=PT0.5S",
})
class SourceQueryTimeoutIT extends AbstractRunIT {

    /** An answer that arrives after 3 s, i.e. after the 0.5 s request timeout. */
    private static final StubNews.Reply SLOW =
        new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 3000);

    @Test
    @SuppressWarnings("unchecked")
    void oneSlowGroupFailsAndTheRunContinues() throws Exception {
        news.responder = req -> req.number() == 1
            ? SLOW
            : StubNews.rss(StubNews.rssItem("Title x" + req.number(), news.baseUrl() + "/rss/articles/x" + req.number(),
                StubNews.pubDate(java.time.Instant.now()), "reuters.com", "https://reuters.com"));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        // group 1 (Q01-Q05) timed out; groups 2-4 answer one article each, attributed to their first element
        assertThat(news.requests).as("a timeout is not retried").hasSize(4);
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status"))).count()).isEqualTo(5);
        assertThat(queries.stream().filter(q -> "OK".equals(q.get("status"))).count()).isEqualTo(3);
        assertThat(queries.stream().filter(q -> "EMPTY".equals(q.get("status"))).count()).isEqualTo(12);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyGroupSlowStillCompletesWithTheNoEvidenceNote() throws Exception {
        news.responder = req -> SLOW;
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitRun(sid, id, 20_000, m -> "FAILED".equals(m.get("status")) || "COMPLETED".equals(m.get("status")));
        // run-control.md FR-47: every group FAILED is no failure any more
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(news.requests).hasSize(4);
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
    }
}
