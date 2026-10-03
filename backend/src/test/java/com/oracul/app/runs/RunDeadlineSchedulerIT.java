package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** generation-runs.md "Slice 11_run-failures" RunDeadlineSchedulerIT: the scheduled tick fails a run past its deadline. */
// @trace FR-32
@TestPropertySource(properties = "oracul.run.deadline-check-interval=PT1S")
class RunDeadlineSchedulerIT extends AbstractDeadlineIT {

    @Test
    void theScheduledTickTimesOutARunPastItsDeadline() throws Exception {
        freshStubs();
        responses.gate("SCENARIO_GENERATION");
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived("SCENARIO_GENERATION", 1, Duration.ofSeconds(10))).isTrue();
        clock.advance(d(180));
        long end = System.currentTimeMillis() + 3000;
        Map<String, Object> run = Map.of();
        while (System.currentTimeMillis() < end) {
            run = runOf(sid, id);
            if ("FAILED".equals(run.get("status"))) break;
            Thread.sleep(200);
        }
        assertThat(run.get("status")).as("no explicit sweep(): the scheduler tick must do it; run=" + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"RUN_TIMEOUT\",\"message\":\"" + T + "\"}"));
        responses.release("SCENARIO_GENERATION");
    }
}
