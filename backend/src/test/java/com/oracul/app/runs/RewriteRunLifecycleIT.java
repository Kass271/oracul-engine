package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * phase-04 story-rewrite.md FR-67, lifecycle half: the deadline (RUNNING and QUEUED), the startup sweep and STOP (FR-45) of a
 * REWRITE run behave as those of stage 10 of a STANDARD run, and never touch the parent. One executor thread, so a rewrite
 * can be kept QUEUED behind a gated run of another session (the technique of RunDeadlineQueuedIT).
 */
// @trace FR-67
@TestPropertySource(properties = {
    "oracul.run.executor-threads=1",
    "oracul.run.deadline-check-interval=PT1H",
})
class RewriteRunLifecycleIT extends AbstractDeadlineIT {

    private static final String BODY = "{\"writingStyle\":{\"mode\":\"NARRATIVE\",\"profileId\":\"kafka\"},\"language\":\"uk\"}";
    private static final String INTERRUPTED = "{\"code\":\"RUN_INTERRUPTED\",\"message\":\"" + I + "\"}";

    private Map<String, Object> startRewrite(String sid, String parentId) throws Exception {
        MvcResult r = mvc.perform(post("/api/runs/" + parentId + "/rewrites").contentType(MediaType.APPLICATION_JSON)
            .content(BODY.getBytes(StandardCharsets.UTF_8)).cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(r.getResponse().getStatus()).as("startRewriteRun: " + r.getResponse().getContentAsString()).isEqualTo(202);
        return json(r.getResponse().getContentAsString());
    }

    private Map<String, Object> stop(String sid, String runId) throws Exception {
        MvcResult r = mvc.perform(post("/api/runs/" + runId + "/stop").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(r.getResponse().getStatus()).as("stopRun: " + r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r.getResponse().getContentAsString());
    }

    /** The parent: a completed standard run of its own session. */
    private Ran parent() throws Exception {
        freshStubs();
        Ran p = runWith(connectedSid(), A);
        assertStoryCompleted(p.run());
        return p;
    }

    private int modelCalls(String runId) {
        return jdbc.queryForObject("select count(*) from model_call where run_id = cast(? as uuid)", Integer.class, runId);
    }

    /** A rewrite that is RUNNING with its STORY_WRITING request held by the stub. */
    private record Held(Ran parent, String id, Map<String, Object> parentRow) {}

    private Held holdRunning() throws Exception {
        Ran p = parent();
        Map<String, Object> parentRow = row(p.id());
        int story0 = requests(STORY).size();
        responses.gate(STORY);
        String id = (String) startRewrite(p.sid(), p.id()).get("id");
        assertThat(responses.awaitArrived(STORY, story0 + 1, Duration.ofSeconds(10))).as("the rewrite's STORY_WRITING request arrived").isTrue();
        waitForStage(p.sid(), id, "WRITING_STORY");
        return new Held(p, id, parentRow);
    }

    /** A rewrite that is QUEUED behind a gated run of another session on the only executor thread. */
    private Held holdQueued() throws Exception {
        Ran p = parent();
        Map<String, Object> parentRow = row(p.id());
        int q0 = requests("QUERY_GENERATION").size();
        responses.gate("QUERY_GENERATION");
        String sx = connectedSid();
        startOk(sx, A);
        assertThat(responses.awaitArrived("QUERY_GENERATION", q0 + 2, Duration.ofSeconds(10))).as("both pipelines' calls of the other run arrived").isTrue();
        Map<String, Object> created = startRewrite(p.sid(), p.id());
        assertThat(created.get("status")).isEqualTo("QUEUED");
        return new Held(p, (String) created.get("id"), parentRow);
    }

    // ---- deadline ---------------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void aRunningRewritePastItsDeadlineIsTimedOutInTheStoryStageAndDoesNothingMore() throws Exception {
        Held h = holdRunning();
        clock.advance(d(180));
        assertThat(sweep(SCHEDULER)).isEqualTo(1);
        assertTimedOut(runOf(h.parent().sid(), h.id()), "WRITING_STORY", 10);

        responses.release(STORY);
        Map<String, Object> ended = row(h.id());
        int story = requests(STORY).size();
        watchUnchanged(h.id(), ended, 1500, () -> assertThat(requests(STORY)).hasSize(story));
        assertThat(storyRows(h.id())).isZero();
        assertThat(row(h.parent().id())).as("the parent row").isEqualTo(h.parentRow());
        assertThat(runOf(h.parent().sid(), h.parent().id())).isEqualTo(h.parent().run());
    }

    // @trace FR-67
    @Test
    void aQueuedRewritePastItsDeadlineIsTimedOutWhenItsTaskStartsAndMakesNoRequest() throws Exception {
        Held h = holdQueued();
        int story0 = requests(STORY).size();
        clock.advance(d(180));
        responses.release("QUERY_GENERATION");
        Map<String, Object> run = awaitRun(h.parent().sid(), h.id(), 10_000, m -> "FAILED".equals(m.get("status")));
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"RUN_TIMEOUT\",\"message\":\"" + T + "\"}"));
        assertThat(run.get("stage")).isNull();
        assertThat(run.get("stageLabel")).isNull();
        assertThat(run.get("stageIndex")).isEqualTo(0);
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(modelCalls(h.id())).as("a timed-out rewrite sends nothing").isZero();
        assertThat(storyRows(h.id())).isZero();
        assertThat(requests(STORY).size()).as("no further STORY_WRITING request of the rewrite")
            .isLessThanOrEqualTo(story0 + 1);
        assertThat(row(h.parent().id())).isEqualTo(h.parentRow());
    }

    // ---- startup sweep ------------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void theStartupSweepFailsARunningRewriteAsInterrupted() throws Exception {
        Held h = holdRunning();
        assertThat(sweep(STARTUP)).isEqualTo(1);
        Map<String, Object> run = runOf(h.parent().sid(), h.id());
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json(INTERRUPTED));
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(run.get("stage")).isEqualTo("WRITING_STORY");
        assertThat(run.get("stageIndex")).isEqualTo(10);

        responses.release(STORY);
        Map<String, Object> ended = row(h.id());
        watchUnchanged(h.id(), ended, 1500, () -> assertThat(storyRows(h.id())).isZero());
        assertThat(row(h.parent().id())).isEqualTo(h.parentRow());
    }

    // @trace FR-67
    @Test
    void theStartupSweepFailsAQueuedRewriteAsInterrupted() throws Exception {
        Held h = holdQueued();
        // the sweep fails every active run: the gated run of the other session and the queued rewrite
        assertThat(sweep(STARTUP)).isEqualTo(2);
        Map<String, Object> run = runOf(h.parent().sid(), h.id());
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json(INTERRUPTED));
        assertThat(run.get("stageIndex")).isEqualTo(0);
        assertThat(row(h.parent().id())).isEqualTo(h.parentRow());
    }

    // ---- STOP -------------------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void stoppingARunningRewriteStopsItAndTheLateAnswerChangesNothing() throws Exception {
        Held h = holdRunning();
        Map<String, Object> stopped = stop(h.parent().sid(), h.id());
        assertThat(stopped.get("status")).isEqualTo("STOPPED");
        assertThat(stopped.get("stage")).isEqualTo("WRITING_STORY");
        assertThat(stopped.get("stageIndex")).isEqualTo(10);
        assertThat(stopped.get("failure")).isNull();
        assertThat(stopped.get("headline")).isNull();
        assertThat(stopped.get("completedAt")).isNotNull();

        responses.release(STORY);
        Map<String, Object> ended = row(h.id());
        watchUnchanged(h.id(), ended, 1500, () -> assertThat(storyRows(h.id())).isZero());
        assertThat(runOf(h.parent().sid(), h.id()).get("status")).isEqualTo("STOPPED");
        assertThat(row(h.parent().id())).isEqualTo(h.parentRow());
        assertThat(runOf(h.parent().sid(), h.parent().id())).isEqualTo(h.parent().run());
        assertSlotReleased(h.parent().sid());
    }

    // @trace FR-67
    @Test
    void stoppingAQueuedRewriteStopsItBeforeItSendsAnything() throws Exception {
        Held h = holdQueued();
        Map<String, Object> stopped = stop(h.parent().sid(), h.id());
        assertThat(stopped.get("status")).isEqualTo("STOPPED");
        assertThat(stopped.get("stageIndex")).isEqualTo(0);
        assertThat(stopped.get("stage")).isNull();
        assertThat(stopped.get("failure")).isNull();

        int story = requests(STORY).size();
        responses.release("QUERY_GENERATION");
        Map<String, Object> ended = row(h.id());
        watchUnchanged(h.id(), ended, 1500, () -> assertThat(modelCalls(h.id())).isZero());
        assertThat(requests(STORY).size()).as("the stopped rewrite sends no STORY_WRITING request").isLessThanOrEqualTo(story + 1);
        assertThat(runOf(h.parent().sid(), h.id()).get("status")).isEqualTo("STOPPED");
        assertThat(row(h.parent().id())).isEqualTo(h.parentRow());
    }
}
