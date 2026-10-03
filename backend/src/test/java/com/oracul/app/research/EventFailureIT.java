package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.oracul.app.chatgpt.StubOpenAi;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

/** Rows 14-17 of research-pipeline.md "Slice 06_events": ChatGPT transport failures of EVENT_NORMALIZATION / EVENT_CLASSIFICATION. */
// @trace FR-14, FR-15
@TestPropertySource(properties = {"oracul.research.query-budget=18", "oracul.events.max-sources=1000"}) // F240 for row 35
class EventFailureIT extends AbstractEventIT {

    static final String RATE_LIMITED = "ChatGPT plan limit reached — try again later";
    static final String UNAVAILABLE = "ChatGPT is unavailable right now — try again later";
    static final String EXPIRED = "ChatGPT session expired — please reconnect";
    static final String PROVIDER_BODY = "{\"error\":\"PROVIDER-SECRET-BODY\"}";

    /** V4 sources and the normal answers for every purpose except the failing one. */
    private void prepare(String failing) {
        gdeltArticles(v4());
        if (!NORMALIZATION.equals(failing)) script(NORMALIZATION, N_V4);
        if (!CLASSIFICATION.equals(failing)) script(CLASSIFICATION, C_V4);
    }

    private void assertFailed(Ran r, String code, String message, String purpose, int requestsOfPurpose) throws Exception {
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"));
        assertThat(run.get("stage")).isEqualTo("CONNECTING_SIGNALS");
        assertThat(run.get("stageIndex")).isEqualTo(5);
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(requests(purpose)).hasSize(requestsOfPurpose);
        assertThat(json(eventsRaw(r.sid(), r.id()))).isEqualTo(json("{\"items\":[]}"));
        assertThat(counts(run).get("uniqueEvents")).isEqualTo(0);
        assertThat(counts(researchBody(r.sid(), r.id())).get("uniqueEvents")).isEqualTo(0);
        for (Map<String, Object> s : sourceItems(r.sid(), r.id())) assertThat((List<?>) s.get("entities")).isEmpty();
        assertThat(eventRows(r.id())).isEqualTo(0);
        String bodies = getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString();
        assertThat(bodies).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("STUBSECRET");
    }

    private void assertSlotReleased(Ran r) throws Exception {
        assertThat(startRun(r.sid(), B).andReturn().getResponse().getStatus()).as("slot released").isEqualTo(202);
    }

    private void assertCompletedAsV4(Ran r) throws Exception {
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(2);
        assertThat(event(events, "EV001").get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(event(events, "EV002").get("sourceIds")).isEqualTo(List.of("S004"));
        assertThat(classification(event(events, "EV001")).get("topic")).isEqualTo("health");
    }

    // #14
    @ParameterizedTest(name = "429 on {0}: failed with CHATGPT_RATE_LIMITED, no retry")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void rateLimitFailsTheRunWithoutRetry(String purpose) throws Exception {
        prepare(purpose);
        always(purpose, StubResponses.status(429, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_RATE_LIMITED", RATE_LIMITED, purpose, 1);
        assertSlotReleased(r);
    }

    // #14: 429 on the retry itself
    @Test
    void rateLimitOnTheNormalizationRetryFailsTheRun() throws Exception {
        gdeltArticles(v4());
        script(NORMALIZATION, "not json");
        always(NORMALIZATION, StubResponses.status(429, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_RATE_LIMITED", RATE_LIMITED, NORMALIZATION, 2);
    }

    // #14: 429 on the follow-up classification batch
    @Test
    void rateLimitOnTheClassificationFollowUpFailsTheRunAndWritesNothing() throws Exception {
        gdeltArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, classifications(C_EV1));
        always(CLASSIFICATION, StubResponses.status(429, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_RATE_LIMITED", RATE_LIMITED, CLASSIFICATION, 2);
    }

    // #15
    @ParameterizedTest(name = "HTTP 500 twice on {0}: failed with CHATGPT_UNAVAILABLE after one retry")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void serverErrorTwiceFailsTheRunAfterOneRetry(String purpose) throws Exception {
        prepare(purpose);
        always(purpose, StubResponses.status(500, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_UNAVAILABLE", UNAVAILABLE, purpose, 2);
        assertSlotReleased(r);
    }

    // #15 connection closed
    @ParameterizedTest(name = "connection closed twice on {0}: CHATGPT_UNAVAILABLE")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void connectionErrorsTwiceFailTheRun(String purpose) throws Exception {
        prepare(purpose);
        always(purpose, new StubResponses.Reply(0, "", 0));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_UNAVAILABLE", UNAVAILABLE, purpose, 2);
    }

    // #16
    @ParameterizedTest(name = "HTTP 503 once on {0}, then normal: completed with 2 requests")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void aSingleTransientFailureIsRetriedTransparently(String purpose) throws Exception {
        gdeltArticles(v4());
        String answer = NORMALIZATION.equals(purpose) ? N_V4 : C_V4;
        scriptReplies(purpose, StubResponses.status(503, PROVIDER_BODY), StubResponses.completed(answer));
        if (!NORMALIZATION.equals(purpose)) script(NORMALIZATION, N_V4);
        if (!CLASSIFICATION.equals(purpose)) script(CLASSIFICATION, C_V4);
        Ran r = run(A);
        assertCompletedAsV4(r);
        assertThat(requests(purpose)).hasSize(2);
    }

    // #17
    @ParameterizedTest(name = "401 always on {0} and refresh refused: CHATGPT_SESSION_EXPIRED")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void anUnrecoverableSessionFailsTheRun(String purpose) throws Exception {
        prepare(purpose);
        String sid = connectedSid();
        stub.responder = req -> "refresh_token".equals(req.form().get("grant_type"))
            ? StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}")
            : stub.ok(3600, StubOpenAi.ALL_SCOPES, true);
        always(purpose, StubResponses.status(401, PROVIDER_BODY));
        Ran r = runWith(sid, A);
        assertFailed(r, "CHATGPT_SESSION_EXPIRED", EXPIRED, purpose, 1);
        String conn = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
            .andReturn().getResponse().getContentAsString();
        assertThat(json(conn).get("state")).isEqualTo("SESSION_EXPIRED");
    }

    // 401 / 403 table row: one token refresh + one retry
    @ParameterizedTest(name = "HTTP {0} once: token refreshed once, call retried")
    @ValueSource(ints = {401, 403})
    void aRejectedTokenIsRefreshedOnceAndTheCallRetried(int status) throws Exception {
        for (String purpose : List.of(NORMALIZATION)) {
            gdeltArticles(v4());
            String sid = connectedSid();
            int refreshesBefore = stub.grant("refresh_token").size();
            scriptReplies(purpose, StubResponses.status(status, PROVIDER_BODY), StubResponses.completed(N_V4));
            script(CLASSIFICATION, C_V4);
            Ran r = runWith(sid, A);
            assertCompletedAsV4(r);
            assertThat(requests(purpose)).hasSize(2);
            assertThat(stub.grant("refresh_token")).as("exactly one refresh").hasSize(refreshesBefore + 1);
            assertThat(requests(purpose).get(1).headers().get("authorization"))
                .isNotEqualTo(requests(purpose).get(0).headers().get("authorization"));
        }
    }

    // 200 with status != completed is a content problem, not a transport failure
    @Test
    void anIncompleteClassificationResponseIsAContentFailureNotATransportFailure() throws Exception {
        gdeltArticles(v4());
        script(NORMALIZATION, N_V4);
        StubResponses.Reply incomplete = new StubResponses.Reply(200, "{\"id\":\"resp_1\",\"status\":\"incomplete\",\"output\":[]}", 0);
        always(CLASSIFICATION, incomplete);
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(CLASSIFICATION)).hasSize(2);
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(2).allSatisfy(e -> {
            assertThat(e.get("excludedReason")).isEqualTo("CLASSIFICATION_FAILED");
            assertThat(omitted(e, "classification")).isTrue();
        });
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(2);
    }

    // #35: a transport failure of one parallel batch stops the stage: batches that have not started never start
    @Test
    void aRateLimitedBatchStopsTheStageBeforeLaterBatchesStart() throws Exception {
        gdeltF240();
        // the 429 is held until all four requests have arrived, so batch 4 has certainly passed its gate
        always(NORMALIZATION, req -> StubResponses.batch(req) == 2
            ? holdUntilFourArrived()
            : StubResponses.delayed(StubResponses.completed(StubResponses.defaultNormalization(req.inputText())), 500));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_RATE_LIMITED", RATE_LIMITED, NORMALIZATION, 4);
        assertThat(requests(NORMALIZATION).stream().map(StubResponses::batch).sorted().toList()).containsExactly(1, 2, 3, 4);
        assertThat(requests(CLASSIFICATION)).isEmpty();
        // the answers of the batches that were in flight are discarded and nothing else is sent afterwards
        Thread.sleep(1500);
        assertThat(requests(NORMALIZATION)).as("batches 5 and 6 never start").hasSize(4);
        assertThat(requests(CLASSIFICATION)).isEmpty();
        assertThat(eventRows(r.id())).isEqualTo(0);
        assertThat(counts(getRunBody(r)).get("uniqueEvents")).isEqualTo(0);
        assertThat(getRunBody(r).get("failure")).isEqualTo(json("{\"code\":\"CHATGPT_RATE_LIMITED\",\"message\":\"" + RATE_LIMITED + "\"}"));
    }

    private StubResponses.Reply holdUntilFourArrived() {
        responses.awaitArrived(NORMALIZATION, 4, java.time.Duration.ofSeconds(10));
        return StubResponses.status(429, PROVIDER_BODY);
    }

    private Map<String, Object> getRunBody(Ran r) throws Exception {
        return json(getRun(r.sid(), r.id()));
    }
}
