package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.oracul.app.chatgpt.StubOpenAi;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Rows 31-34 of research-pipeline.md "Slice 06_events" (NFR-2, FR-32): the run guard stops stage 5 when the run is no
 * longer RUNNING or past its deadline. One batch per source and concurrency 1 make "no further request" observable.
 */
// @trace FR-14, FR-15, FR-39
@TestPropertySource(properties = {
    "oracul.events.normalization-batch-size=1",
    "oracul.events.normalization-concurrency=1",
    "oracul.run.timeout=PT4S",
    "oracul.openai.timeout=PT20S",
    "oracul.openai.retry-delay=PT5S",
})
class EventRunGuardIT extends AbstractEventIT {

    private static final String TIMEOUT_MESSAGE = "Generation took too long — try again";

    private Map<String, Object> runRow(String runId) {
        return jdbc.queryForMap("select status, stage, failure_code, failure_message, completed_at, updated_at "
            + "from generation_run where id = cast(? as uuid)", runId);
    }

    private void failRunBehindThePipelinesBack(String runId) {
        int updated = jdbc.update("update generation_run set status = 'FAILED', failure_code = 'RUN_TIMEOUT', failure_message = ?, "
            + "completed_at = now() where id = cast(? as uuid) and status = 'RUNNING'", TIMEOUT_MESSAGE, runId);
        assertThat(updated).as("the run must be RUNNING while the gated request is held").isEqualTo(1);
    }

    /** Nothing of step 5 was written and the stage never advanced. */
    private void assertNothingWritten(String sid, String runId) throws Exception {
        assertThat(json(eventsRaw(sid, runId))).isEqualTo(json("{\"items\":[]}"));
        assertThat(eventRows(runId)).isEqualTo(0);
        Map<String, Object> run = json(getRun(sid, runId));
        assertThat(counts(run).get("uniqueEvents")).isEqualTo(0);
        assertThat(run.get("stage")).isEqualTo("CONNECTING_SIGNALS");
        assertThat(run.get("stageIndex")).isEqualTo(5);
        for (Map<String, Object> s : sourceItems(sid, runId)) assertThat((List<?>) s.get("entities")).isEmpty();
    }

    private void assertTimeoutFailure(Map<String, Object> run) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"RUN_TIMEOUT\",\"message\":\"" + TIMEOUT_MESSAGE + "\"}"));
        assertThat(run.get("completedAt")).isNotNull();
    }

    private void assertSlotReleased(String sid) throws Exception {
        assertThat(startRun(sid, B).andReturn().getResponse().getStatus()).as("active-run slot released").isEqualTo(202);
    }

    /** Releases the gate and watches 2.5 s: no request beyond the expected counts, the run row untouched by the pipeline. */
    private void releaseAndWatch(String gated, int expectedNormalization, int expectedClassification, String runId,
                                 Map<String, Object> rowBefore) throws Exception {
        responses.release(gated);
        long end = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < end) {
            assertThat(requests(NORMALIZATION)).as("EVENT_NORMALIZATION requests").hasSize(expectedNormalization);
            assertThat(requests(CLASSIFICATION)).as("EVENT_CLASSIFICATION requests").hasSize(expectedClassification);
            assertThat(runRow(runId)).as("the pipeline must not touch a run that another writer ended").isEqualTo(rowBefore);
            Thread.sleep(100);
        }
    }

    // #31
    @Test
    void aRunEndedByAnotherWriterSendsNoFurtherNormalizationRequest() throws Exception {
        newsArticles(v4());
        responses.gate(NORMALIZATION);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived(NORMALIZATION, 1, Duration.ofSeconds(5))).as("first batch arrived").isTrue();
        failRunBehindThePipelinesBack(id);
        Map<String, Object> before = runRow(id);
        releaseAndWatch(NORMALIZATION, 1, 0, id, before);

        Map<String, Object> run = json(getRun(sid, id));
        assertTimeoutFailure(run);
        assertNothingWritten(sid, id);
        assertSlotReleased(sid);
    }

    // #32
    @Test
    void aRunEndedByAnotherWriterSendsNoFurtherClassificationRequestAndWritesNoEvents() throws Exception {
        newsArticles(v4());
        responses.gate(CLASSIFICATION);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived(CLASSIFICATION, 1, Duration.ofSeconds(5))).as("classification arrived").isTrue();
        failRunBehindThePipelinesBack(id);
        Map<String, Object> before = runRow(id);
        releaseAndWatch(CLASSIFICATION, 4, 1, id, before);

        assertTimeoutFailure(json(getRun(sid, id)));
        assertNothingWritten(sid, id);
        assertSlotReleased(sid);
    }

    // #33
    @Test
    void aRunPastItsDeadlineStopsCallingChatGptAndEndsWithRunTimeout() throws Exception {
        newsArticles(v4());
        responses.delay(NORMALIZATION, Duration.ofSeconds(6));
        Ran r = runWithin(A, 20_000);
        assertTimeoutFailure(r.run());
        assertThat(r.run().get("stage")).isEqualTo("CONNECTING_SIGNALS");
        assertThat(requests(NORMALIZATION)).as("the late answer is discarded, no further batch is sent").hasSize(1);
        assertThat(requests(CLASSIFICATION)).isEmpty();
        Thread.sleep(500);
        assertThat(requests(NORMALIZATION)).hasSize(1);
        assertNothingWritten(r.sid(), r.id());
        assertSlotReleased(r.sid());
    }

    // #34
    @Test
    void theTransportRetryIsNotSentAfterTheDeadline() throws Exception {
        newsArticles(v4());
        always(NORMALIZATION, StubResponses.status(503, "{\"error\":\"PROVIDER-SECRET-BODY\"}"));
        Ran r = runWithin(A, 20_000);
        assertThat(requests(NORMALIZATION)).as("the retry after the 5 s retry delay is not sent past the 4 s deadline").hasSize(1);
        assertTimeoutFailure(r.run());
        assertThat(r.run().get("failure")).isNotEqualTo(json(
            "{\"code\":\"CHATGPT_UNAVAILABLE\",\"message\":\"ChatGPT is temporarily unavailable — try again in a few minutes\"}"));
        assertThat(eventRows(r.id())).isEqualTo(0);
        assertNothingWritten(r.sid(), r.id());
    }

    // R12: an abandoned batch task must not refresh the token or expire the connection
    @Test
    void anAbandonedBatchTaskDoesNotRefreshTheTokenOrExpireTheConnection() throws Exception {
        newsArticles(v4());
        // every token answer is valid for 1 s only: the access token is always inside the refresh skew
        stub.responder = req -> stub.ok(1, StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        responses.gate(NORMALIZATION);
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived(NORMALIZATION, 1, Duration.ofSeconds(5))).as("first batch arrived").isTrue();
        int tokenRequestsBefore = stub.requests.size();
        // the refusing token endpoint would turn a (wrongly) forced refresh into SESSION_EXPIRED
        stub.responder = req -> StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}");
        failRunBehindThePipelinesBack(id);
        Map<String, Object> before = runRow(id);
        releaseAndWatch(NORMALIZATION, 1, 0, id, before);

        assertThat(stub.requests).as("no token endpoint request for an abandoned task").hasSize(tokenRequestsBefore);
        String conn = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
            .andReturn().getResponse().getContentAsString();
        assertThat(json(conn).get("state")).isEqualTo("CONNECTED");
    }
}
