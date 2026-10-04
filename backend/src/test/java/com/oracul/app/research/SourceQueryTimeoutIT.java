package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Provider timeout (oracul.news.query-timeout) counts as FAILED, like any other provider error. */
// @trace FR-13, FR-44, FR-47
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=14",
    "oracul.news.query-timeout=PT0.5S",
})
class SourceQueryTimeoutIT extends AbstractRunIT {

    @Test
    @SuppressWarnings("unchecked")
    void oneSlowGroupFailsAndTheRunContinues() throws Exception {
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
        // group 1 (Q01-Q05) timed out; groups 2-4 answer one article each, attributed to their first element
        assertThat(gdelt.requests).as("a timeout is not retried").hasSize(4);
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status"))).count()).isEqualTo(5);
        assertThat(queries.stream().filter(q -> "OK".equals(q.get("status"))).count()).isEqualTo(3);
        assertThat(queries.stream().filter(q -> "EMPTY".equals(q.get("status"))).count()).isEqualTo(12);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyGroupSlowStillCompletesWithTheNoEvidenceNote() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 3000);
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitRun(sid, id, 20_000, m -> "FAILED".equals(m.get("status")) || "COMPLETED".equals(m.get("status")));
        // run-control.md FR-47: every group FAILED is no failure any more
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(gdelt.requests).hasSize(4);
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
    }
}
