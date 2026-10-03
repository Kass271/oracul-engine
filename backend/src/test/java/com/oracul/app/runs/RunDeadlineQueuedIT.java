package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** generation-runs.md "Slice 11_run-failures" RunDeadlineQueuedIT: a QUEUED run past its deadline is timed out. */
// @trace FR-32
@TestPropertySource(properties = {
    "oracul.run.executor-threads=1",
    "oracul.run.deadline-check-interval=PT1H",
})
class RunDeadlineQueuedIT extends AbstractDeadlineIT {

    private record Two(String sx, String x, String sy, String y) {}

    private Two xRunningYQueued() throws Exception {
        freshStubs();
        responses.gate("QUERY_EXPANSION");
        String sx = connectedSid();
        String x = (String) startOk(sx, A).get("id");
        assertThat(responses.awaitArrived("QUERY_EXPANSION", 1, Duration.ofSeconds(10))).isTrue();
        String sy = connectedSid();
        Map<String, Object> queued = startOk(sy, B);
        assertThat(queued.get("status")).isEqualTo("QUEUED");
        return new Two(sx, x, sy, (String) queued.get("id"));
    }

    private void assertQueuedTimeout(Map<String, Object> run) {
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"RUN_TIMEOUT\",\"message\":\"" + T + "\"}"));
        assertThat(run.get("stage")).isNull();
        assertThat(run.get("stageLabel")).isNull();
        assertThat(run.get("stageIndex")).isEqualTo(0);
        assertThat(run.get("completedAt")).isNotNull();
    }

    // #1 + #2
    @Test
    void aQueuedRunPastItsDeadlineIsTimedOutAndMakesNoRequest() throws Exception {
        Two t = xRunningYQueued();
        clock.advance(d(180));
        assertThat(sweep(SCHEDULER)).isEqualTo(2);
        assertQueuedTimeout(runOf(t.sy(), t.y()));

        responses.release("QUERY_EXPANSION");
        Map<String, Object> before = row(t.y());
        watchUnchanged(t.y(), before, 3000, () -> assertThat(requests("QUERY_EXPANSION")).hasSize(1));
        assertSlotReleased(t.sy());
    }

    // #3
    @Test
    void theTaskStartCommitsTheTimeoutOfAStillQueuedRun() throws Exception {
        Two t = xRunningYQueued();
        clock.advance(d(180));
        responses.release("QUERY_EXPANSION");
        Map<String, Object> y = awaitRun(t.sy(), t.y(), 5000, m -> "FAILED".equals(m.get("status")));
        assertQueuedTimeout(y);
        assertThat(requests("QUERY_EXPANSION")).hasSize(1);
    }
}
