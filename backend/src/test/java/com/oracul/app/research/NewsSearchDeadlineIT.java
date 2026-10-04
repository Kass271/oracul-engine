package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44: no request (first attempt or retry) starts at or after the run's deadlineAt; the run then
 * ends RUN_TIMEOUT (phase-01 guard). Real time: deadline 3 s, spacing 1.5 s, every answer takes 1 s.
 */
// @trace FR-44
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.min-stage-duration=PT0S",
    "oracul.run.executor-threads=10",
    "oracul.run.timeout=PT3S",
    "oracul.news.rate-limit-wait=PT0S",
    "oracul.news.search-budget=PT75S",
})
class NewsSearchDeadlineIT extends AbstractRunIT {

    @org.springframework.beans.factory.annotation.Autowired
    SourceRetrieval retrieval;

    /**
     * AbstractRunIT's dynamic properties (StubGdelt.registerAll: spacing PT0S) win over any property of this class, so the
     * 1.5 s spacing is set on the bean itself for this test and restored afterwards.
     */
    @org.junit.jupiter.api.BeforeEach
    void realSpacing() {
        org.springframework.test.util.ReflectionTestUtils.setField(retrieval, "requestSpacing", java.time.Duration.ofMillis(1500));
    }

    @org.junit.jupiter.api.AfterEach
    void restoreSpacing() {
        org.springframework.test.util.ReflectionTestUtils.setField(retrieval, "requestSpacing", java.time.Duration.ZERO);
    }

    @Test
    void noRequestStartsAtOrAfterTheRunDeadline() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(200, "application/json", "{}", 1000);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitRun(sid, id, 20_000, m -> "FAILED".equals(m.get("status")) || "COMPLETED".equals(m.get("status")));
        // starts at ~0.2 s and ~1.7 s; the third would start at ~3.2 s, after the deadline at 3 s: not sent
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json(
            "{\"code\":\"RUN_TIMEOUT\",\"message\":\"Generation took too long — try again\"}"));
        assertThat(gdelt.requests.size()).as("requests before the deadline only").isLessThanOrEqualTo(2);
        Thread.sleep(2500);
        assertThat(gdelt.requests.size()).as("nothing is sent after the run ended").isLessThanOrEqualTo(2);
    }
}
