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
// @trace FR-14, FR-15, FR-38, FR-39, FR-46
// F240 for row 35; news-search.md FR-46 keeps 30 sources, batches of 5 keep the 6 normalisation batches
@TestPropertySource(properties = {"oracul.research.query-budget=18", "oracul.events.max-sources=1000",
    "oracul.events.normalization-batch-size=5"})
class EventFailureIT extends AbstractEventIT {

    static final String RATE_LIMITED = "ChatGPT usage limit reached — try again later";
    static final String UNAVAILABLE = "ChatGPT is temporarily unavailable — try again in a few minutes";
    static final String INCOMPLETE = "ChatGPT did not finish the answer — please try again";
    static final String EXPIRED = "ChatGPT session expired — please reconnect";
    static final String PROVIDER_BODY = "{\"error\":\"PROVIDER-SECRET-BODY\"}";

    /** V4 sources and the normal answers for every purpose except the failing one. */
    private void prepare(String failing) {
        newsArticles(v4());
        if (!NORMALIZATION.equals(failing)) script(NORMALIZATION, N_V4);
        if (!CLASSIFICATION.equals(failing)) script(CLASSIFICATION, C_V4);
    }

    private void assertFailed(Ran r, String code, String message, String purpose, int requestsOfPurpose) throws Exception {
        assertFailed(r, code, message, null, purpose, requestsOfPurpose);
    }

    private void assertFailed(Ran r, String code, String message, String providerCode, String purpose, int requestsOfPurpose)
        throws Exception {
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"" + code + "\",\"message\":\"" + message + "\""
            + (providerCode == null ? "" : ",\"providerCode\":\"" + providerCode + "\"") + "}"));
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
        newsArticles(v4());
        script(NORMALIZATION, "not json");
        always(NORMALIZATION, StubResponses.status(429, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_RATE_LIMITED", RATE_LIMITED, NORMALIZATION, 2);
    }

    // #14: 429 on the follow-up classification batch
    @Test
    void rateLimitOnTheClassificationFollowUpFailsTheRunAndWritesNothing() throws Exception {
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, classifications(C_EV1));
        always(CLASSIFICATION, StubResponses.status(429, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_RATE_LIMITED", RATE_LIMITED, CLASSIFICATION, 2);
    }

    // #15 (FR-39 row 4: at most 2 retries, 3 identical requests)
    @ParameterizedTest(name = "HTTP 500 always on {0}: failed with CHATGPT_UNAVAILABLE after two retries")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void serverErrorAlwaysFailsTheRunAfterTwoRetries(String purpose) throws Exception {
        prepare(purpose);
        always(purpose, StubResponses.status(500, PROVIDER_BODY));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_UNAVAILABLE", UNAVAILABLE, purpose, 3);
        List<StubResponses.Request> calls = requests(purpose);
        assertThat(calls.get(1).body()).as("identical body on retry").isEqualTo(calls.get(0).body());
        assertThat(calls.get(2).body()).isEqualTo(calls.get(0).body());
        assertSlotReleased(r);
    }

    // #15 connection closed
    @ParameterizedTest(name = "connection closed always on {0}: CHATGPT_UNAVAILABLE after 3 requests")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void connectionErrorsAlwaysFailTheRun(String purpose) throws Exception {
        prepare(purpose);
        always(purpose, new StubResponses.Reply(0, "", 0));
        Ran r = run(A);
        assertFailed(r, "CHATGPT_UNAVAILABLE", UNAVAILABLE, purpose, 3);
    }

    // #16
    @ParameterizedTest(name = "HTTP 503 once on {0}, then normal: completed with 2 requests")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void aSingleTransientFailureIsRetriedTransparently(String purpose) throws Exception {
        newsArticles(v4());
        String answer = NORMALIZATION.equals(purpose) ? N_V4 : C_V4;
        scriptReplies(purpose, StubResponses.status(503, PROVIDER_BODY), StubResponses.completed(answer));
        if (!NORMALIZATION.equals(purpose)) script(NORMALIZATION, N_V4);
        if (!CLASSIFICATION.equals(purpose)) script(CLASSIFICATION, C_V4);
        Ran r = run(A);
        assertCompletedAsV4(r);
        assertThat(requests(purpose)).hasSize(2);
    }

    // #17 (FR-39 row 1): a 401 never refreshes and never retries
    @ParameterizedTest(name = "401 on {0}: CHATGPT_SESSION_EXPIRED without refresh or retry")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void a401EndsTheSessionWithoutRefreshOrRetry(String purpose) throws Exception {
        prepare(purpose);
        String sid = connectedSid();
        int refreshesBefore = stub.grant("refresh_token").size();
        always(purpose, StubResponses.status(401, PROVIDER_BODY));
        Ran r = runWith(sid, A);
        assertFailed(r, "CHATGPT_SESSION_EXPIRED", EXPIRED, purpose, 1);
        assertThat(stub.grant("refresh_token")).as("no refresh after a 401").hasSize(refreshesBefore);
        String conn = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
            .andReturn().getResponse().getContentAsString();
        assertThat(json(conn).get("state")).isEqualTo("SESSION_EXPIRED");
    }

    // FR-39 row 6: a 403 without a code is an unexpected error (http_403): no refresh, no retry, credentials kept
    @ParameterizedTest(name = "403 without code on {0}: CHATGPT_UNEXPECTED_ERROR http_403")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void a403WithoutACodeIsAnUnexpectedError(String purpose) throws Exception {
        prepare(purpose);
        String sid = connectedSid();
        int refreshesBefore = stub.grant("refresh_token").size();
        always(purpose, StubResponses.status(403, PROVIDER_BODY));
        Ran r = runWith(sid, A);
        assertFailed(r, "CHATGPT_UNEXPECTED_ERROR", "ChatGPT returned an unexpected error (http_403) — please try again",
            "http_403", purpose, 1);
        assertThat(stub.grant("refresh_token")).as("no refresh after a 403").hasSize(refreshesBefore);
        String conn = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
            .andReturn().getResponse().getContentAsString();
        assertThat(json(conn).get("state")).isEqualTo("CONNECTED");
    }

    // FR-38 / FR-39 row 7: a 200 answer with status incomplete fails the run after one request (no content retry)
    @Test
    void anIncompleteClassificationResponseFailsTheRunWithChatGptIncomplete() throws Exception {
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        StubResponses.Reply incomplete = new StubResponses.Reply(200, "{\"id\":\"resp_1\",\"status\":\"incomplete\",\"output\":[]}", 0);
        always(CLASSIFICATION, incomplete);
        Ran r = run(A);
        assertFailed(r, "CHATGPT_INCOMPLETE", INCOMPLETE, CLASSIFICATION, 1);
    }

    // #35: a transport failure of one parallel batch stops the stage: batches that have not started never start
    @Test
    void aRateLimitedBatchStopsTheStageBeforeLaterBatchesStart() throws Exception {
        newsF240();
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
