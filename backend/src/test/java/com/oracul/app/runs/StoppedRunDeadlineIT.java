package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * run-control.md FR-45 rule "STOPPED is never overwritten by RUN_TIMEOUT, FAILED or COMPLETED": neither the deadline sweep, the
 * startup sweep nor the pipeline task that was in flight at the stop changes a STOPPED row.
 */
// @trace FR-45, FR-51
class StoppedRunDeadlineIT extends AbstractDeadlineIT {

    @Test
    void aStoppedRunIsNeverTouchedBySweepsOrTheLateTask() throws Exception {
        freshStubs();
        responses.gate("QUERY_GENERATION");
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived("QUERY_GENERATION", 2, Duration.ofSeconds(10))).as("both pipelines' calls arrived").isTrue();

        MvcResult stop = mvc.perform(post("/api/runs/" + id + "/stop").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(stop.getResponse().getStatus()).as(stop.getResponse().getContentAsString()).isEqualTo(200);
        Map<String, Object> before = row(id);
        assertThat(before.get("status")).isEqualTo("STOPPED");

        clock.advance(d(600)); // far past the run deadline
        assertThat(sweep(SCHEDULER)).as("the deadline sweep ignores a STOPPED run").isEqualTo(0);
        assertThat(sweep(STARTUP)).as("the startup sweep ignores a STOPPED run").isEqualTo(0);
        assertThat(row(id)).isEqualTo(before);

        responses.release("QUERY_GENERATION"); // the task in flight wakes up after the deadline: it must not touch the row either
        watchUnchanged(id, before, 2000, () -> assertThat(requests("QUERY_GENERATION")).hasSize(2));
        Map<String, Object> run = runOf(sid, id);
        assertThat(run.get("status")).isEqualTo("STOPPED");
        assertThat(run.get("failure")).isNull();
        assertThat(run.get("headline")).isNull();
        assertSlotReleased(sid);
    }
}
