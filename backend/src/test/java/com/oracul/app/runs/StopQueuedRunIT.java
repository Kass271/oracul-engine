package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.result.AbstractStoryIT;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * run-control.md FR-45 rule "a QUEUED run that is stopped before the executor picks it up never starts": a single executor
 * thread is held by the first session's run, the second session's run waits QUEUED, is stopped, and must never send a request.
 */
// @trace FR-45
@TestPropertySource(properties = "oracul.run.executor-threads=1")
class StopQueuedRunIT extends AbstractStoryIT {

    @Test
    void aQueuedRunThatIsStoppedNeverStarts() throws Exception {
        responses.gate("QUERY_EXPANSION");
        String sidA = connectedSid();
        String sidB = connectedSid();
        String first = (String) startOk(sidA, A).get("id");
        assertThat(responses.awaitArrived("QUERY_EXPANSION", 1, Duration.ofSeconds(15))).as("run 1 holds the only thread").isTrue();
        String queued = (String) startOk(sidB, A).get("id");
        try {
            Map<String, Object> waiting = json(getRun(sidB, queued));
            assertThat(waiting.get("status")).as("run 2 waits for the executor").isEqualTo("QUEUED");

            MvcResult r = mvc.perform(post("/api/runs/" + queued + "/stop").cookie(new Cookie("ORACUL_SID", sidB))).andReturn();
            assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
            Map<String, Object> stopped = json(r.getResponse().getContentAsString());
            assertThat(stopped.get("status")).isEqualTo("STOPPED");
            assertThat(stopped.get("stageIndex")).isEqualTo(0);
            assertThat(stopped.get("stage")).isNull();
            assertThat(stopped.get("completedAt")).isNotNull();
            Map<String, Object> rowAtStop = jdbc.queryForMap("select * from generation_run where id = cast(? as uuid)", queued);

            responses.release("QUERY_EXPANSION");
            Map<String, Object> done = awaitRun(sidA, first, 15_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
            assertThat(done.get("status")).as("run 1 is not affected").isEqualTo("COMPLETED");
            Thread.sleep(500); // the executor has now taken the task of run 2 and must have dropped it

            assertThat(jdbc.queryForMap("select * from generation_run where id = cast(? as uuid)", queued))
                .as("a stopped QUEUED run is never started, failed or timed out").isEqualTo(rowAtStop);
            assertThat(requests("QUERY_EXPANSION")).as("only run 1 expanded queries").hasSize(1);
            assertThat(responses.modelRequests).as("only run 1 read the model catalogue").hasSize(1);
            assertThat(gdelt.requests).as("only run 1 searched").hasSize(4);
            assertThat(startRun(sidB, B).andReturn().getResponse().getStatus()).as("the slot of the stopped run is free").isEqualTo(202);
        } finally {
            responses.release("QUERY_EXPANSION");
        }
    }
}
