package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubNews;
import com.oracul.app.research.StubResponses;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** generation-runs.md "Slice 11_run-failures" RunDeadlineIT rows 1-7: scheduler sweep, boundary, no further work. */
// @trace FR-32, FR-47
// @trace NFR-2
class RunDeadlineIT extends AbstractDeadlineIT {

    private String startGated(String purpose, String[] idOut) throws Exception {
        freshStubs();
        responses.gate(purpose);
        String sid = connectedSid();
        idOut[0] = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived(purpose, 1, Duration.ofSeconds(10))).as(purpose + " arrived").isTrue();
        return sid;
    }

    // #1
    @Test
    void aRunOneSecondBeforeItsDeadlineIsNotTimedOut() throws Exception {
        String[] id = new String[1];
        String sid = startGated("SCENARIO_GENERATION", id);
        clock.advance(d(179));
        assertThat(sweep(SCHEDULER)).isEqualTo(0);
        Map<String, Object> run = runOf(sid, id[0]);
        assertThat(run.get("status")).isEqualTo("RUNNING");
        assertThat(run.get("stage")).isEqualTo("EXPLORING_FUTURES");
    }

    // #2 + #3
    @Test
    void aRunAtItsDeadlineIsTimedOutAndTheLateResultIsDiscarded() throws Exception {
        String[] id = new String[1];
        String sid = startGated("SCENARIO_GENERATION", id);
        clock.advance(d(179));
        assertThat(sweep(SCHEDULER)).isEqualTo(0);
        clock.advance(d(1));
        assertThat(sweep(SCHEDULER)).isEqualTo(1);
        Map<String, Object> run = runOf(sid, id[0]);
        assertTimedOut(run, "EXPLORING_FUTURES", 7);
        assertThat(instantOf(run.get("completedAt"))).isEqualTo(clock.instant());
        assertThat(run.get("headline")).isNull();

        responses.release("SCENARIO_GENERATION");
        Map<String, Object> before = row(id[0]);
        watchUnchanged(id[0], before, 2500, () -> {
            assertThat(requests("SCENARIO_CRITIC")).isEmpty();
            assertThat(requests("STORY_WRITING")).isEmpty();
        });
        assertThat(storyRows(id[0])).isEqualTo(0);
        assertResultNotReady(getResult(sid, id[0]));
        assertSlotReleased(sid);
        assertThat(sweep(SCHEDULER)).isEqualTo(0);
    }

    // #4
    @Test
    void aTimedOutSearchStageStoresNothingAfterTheSweep() throws Exception {
        freshStubs();
        news.responder = req -> new StubNews.Reply(200, "application/rss+xml", "<rss version=\"2.0\"><channel></channel></rss>", 3000);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        waitForStage(sid, id, "SEARCHING");
        clock.advance(d(180));
        assertThat(sweep(SCHEDULER)).isEqualTo(1);
        assertTimedOut(runOf(sid, id), "SEARCHING", 3);
        Map<String, Object> before = row(id);
        Thread.sleep(5000);
        assertThat(row(id)).isEqualTo(before);
        assertThat(counts(runOf(sid, id)).values()).containsOnly(0);
        assertThat(sourceItems(sid, id)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from source where run_id = cast(? as uuid)", Integer.class, id)).isEqualTo(0);
        assertThat(requests(NORMALIZATION)).isEmpty();
    }

    // #5
    @Test
    void thePipelineItselfCommitsTheTimeoutWhenNobodySweeps() throws Exception {
        String[] id = new String[1];
        String sid = startGated("QUERY_EXPANSION", id);
        clock.advance(d(180));
        responses.release("QUERY_EXPANSION");
        Map<String, Object> run = awaitRun(sid, id[0], 5000, m -> "FAILED".equals(m.get("status")));
        assertTimedOut(run, "RESEARCH_STRATEGY", 2);
        assertThat(news.requests).as("the check before SEARCHING stops the task").isEmpty();
    }

    // #6
    @Test
    void terminalRunsAreNeverTouchedBySweep() throws Exception {
        freshStubs();
        String completedSid = connectedSid();
        Map<String, Object> completed = runWith(completedSid, A).run();
        assertThat(completed.get("status")).isEqualTo("COMPLETED");
        Map<String, Object> completedBefore = row((String) completed.get("id"));

        // run-control.md FR-47: news down no longer fails a run; an unparsable scenario answer does (INVALID_SCENARIO)
        freshStubs();
        var normal = responses.defaultResponder(); // freshStubs() reset the stub responder: script the failure directly
        responses.responder = req -> "SCENARIO_GENERATION".equals(StubResponses.purpose(req))
            ? StubResponses.completed("not json") : normal.apply(req);
        String failedSid = connectedSid();
        Map<String, Object> failed = runWith(failedSid, A).run();
        assertThat(failed.get("status")).isEqualTo("FAILED");
        Map<String, Object> failedBefore = row((String) failed.get("id"));

        freshStubs();
        responses.gate("QUERY_EXPANSION");
        String activeSid = connectedSid();
        String activeId = (String) startOk(activeSid, A).get("id");
        assertThat(responses.awaitArrived("QUERY_EXPANSION", 1, Duration.ofSeconds(10))).isTrue();

        clock.advance(d(180));
        assertThat(sweep(SCHEDULER)).isEqualTo(1);
        assertThat(row((String) completed.get("id"))).isEqualTo(completedBefore);
        assertThat(row((String) failed.get("id"))).isEqualTo(failedBefore);
        assertThat(runOf(failedSid, (String) failed.get("id")).get("failure")).isEqualTo(json(
            "{\"code\":\"INVALID_SCENARIO\",\"message\":\"ORACUL could not construct a valid scenario\"}"));
        assertTimedOut(runOf(activeSid, activeId), "RESEARCH_STRATEGY", 2);
    }

    // #7
    @ParameterizedTest(name = "timeout while {0} is in flight")
    @ValueSource(strings = {"EVENT_NORMALIZATION", "STORY_WRITING"})
    void aTimeoutDuringAChatGptRequestStopsEverythingAfterIt(String purpose) throws Exception {
        String[] id = new String[1];
        String sid = startGated(purpose, id);
        clock.advance(d(180));
        assertThat(sweep(SCHEDULER)).isEqualTo(1);
        boolean story = "STORY_WRITING".equals(purpose);
        assertTimedOut(runOf(sid, id[0]), story ? "WRITING_STORY" : "CONNECTING_SIGNALS", story ? 10 : 5);
        responses.release(purpose);
        Map<String, Object> before = row(id[0]);
        int totalAtTimeout = responses.requests.size();
        watchUnchanged(id[0], before, 2500, () -> assertThat(responses.requests)
            .as("no ChatGPT request after the timeout, purposes: " + purposes()).hasSize(totalAtTimeout));
    }
}
