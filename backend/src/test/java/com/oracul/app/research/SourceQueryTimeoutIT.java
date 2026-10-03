package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Provider timeout (oracul.news.query-timeout) counts as FAILED, like any other provider error. */
// @trace FR-13
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=14",
    "oracul.news.query-timeout=PT0.5S",
})
class SourceQueryTimeoutIT extends AbstractRunIT {

    @Test
    @SuppressWarnings("unchecked")
    void oneSlowQueryFailsAndTheRunContinues() throws Exception {
        gdelt.responder = req -> req.number() == 1
            ? new StubGdelt.Reply(200, "application/json", "{}", 3000)
            : StubGdelt.json(StubGdelt.articles(List.of(StubGdelt.article(gdelt.baseUrl() + "/articles/x" + req.number(),
                "Title x" + req.number(), "reuters.com", "English", StubGdelt.seendate(java.time.Instant.now())))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status"))).count()).isEqualTo(1);
        assertThat(queries.stream().filter(q -> "OK".equals(q.get("status"))).count()).isEqualTo(19);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyQuerySlowEndsWithNewsUnavailable() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 3000);
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitRun(sid, id, 20_000, m -> "FAILED".equals(m.get("status")) || "COMPLETED".equals(m.get("status")));
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(((Map<String, Object>) run.get("failure")).get("code")).isEqualTo("NEWS_UNAVAILABLE");
    }
}
