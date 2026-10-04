package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 chatgpt-inference.md FR-39: every documented OpenAI error of a stage call (EVENT_NORMALIZATION) and of
 * GET /models ends in the message of its table row; retries only for row 4 (2 retries, backoff 1x then 2x).
 * Exhaustive over the rows, the provider-code sanitizing classes and the three error channels of a stream.
 */
// @trace FR-39, FR-47
@TestPropertySource(properties = {"oracul.openai.retry-delay=PT0.1S", "oracul.openai.timeout=PT0.5S"})
class ErrorClassificationIT extends AbstractPlanUsageIT {

    static final long RETRY_DELAY_MS = 100;

    /** One class of the table: the provider answer and everything that must follow from it. */
    record Row(String name, StubResponses.Reply reply, String code, String message, String providerCode, int requests, String state) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static String unexpected(String code) {
        return "ChatGPT returned an unexpected error (" + code + ") — please try again";
    }

    private static Row row1(String name, StubResponses.Reply reply) {
        return new Row(name, reply, "CHATGPT_SESSION_EXPIRED", M_EXPIRED, null, 1, "SESSION_EXPIRED");
    }

    private static Row row2(String name, StubResponses.Reply reply) {
        return new Row(name, reply, "CHATGPT_PLAN_NOT_ELIGIBLE", M_NOT_ELIGIBLE, null, 1, "CONNECTED");
    }

    private static Row row3(String name, StubResponses.Reply reply) {
        return new Row(name, reply, "CHATGPT_RATE_LIMITED", M_RATE_LIMITED, null, 1, "CONNECTED");
    }

    private static Row row4(String name, StubResponses.Reply reply) {
        return new Row(name, reply, "CHATGPT_UNAVAILABLE", M_UNAVAILABLE, null, 3, "CONNECTED");
    }

    private static Row row5(String name, StubResponses.Reply reply, String providerCode) {
        return new Row(name, reply, "CHATGPT_REQUEST_REJECTED", M_REJECTED, providerCode, 1, "CONNECTED");
    }

    private static Row row6(String name, StubResponses.Reply reply, String providerCode) {
        return new Row(name, reply, "CHATGPT_UNEXPECTED_ERROR", unexpected(providerCode), providerCode, 1, "CONNECTED");
    }

    private static final String SECRET_BODY = "{\"error\":\"PROVIDER-SECRET-BODY\"}";

    static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        // row 1
        rows.add(row1("401 {}", StubResponses.status(401, "{}")));
        rows.add(row1("401 invalid_token", StubResponses.error(401, "invalid_token")));
        rows.add(row1("401 user_not_eligible code", StubResponses.error(401, "subscription_sharing_user_not_eligible")));
        rows.add(row1("401 secret body", StubResponses.status(401, SECRET_BODY)));
        for (String code : List.of("subscription_sharing_invalid_user", "chatpass_v2_scope_not_authorized",
            "chatpass_v2_invalid_authorization_context")) {
            for (int status : List.of(400, 403)) rows.add(row1(status + " " + code, StubResponses.error(status, code)));
        }
        // row 2
        rows.add(row2("403 user_not_eligible", StubResponses.error(403, "subscription_sharing_user_not_eligible")));
        // row 3
        rows.add(row3("429 usage_limit_exceeded", StubResponses.error(429, "subscription_sharing_usage_limit_exceeded")));
        rows.add(row3("429 {}", StubResponses.status(429, "{}")));
        rows.add(row3("429 string error", StubResponses.status(429, "{\"error\":\"rate_limited\"}")));
        rows.add(row3("429 secret body", StubResponses.status(429, SECRET_BODY)));
        rows.add(row3("429 non-JSON", new StubResponses.Reply(429, "Please limit requests to one every 5 seconds", 0, "text/plain", null, 0)));
        // row 4
        rows.add(row4("503 usage_unavailable", StubResponses.error(503, "subscription_sharing_usage_unavailable")));
        rows.add(row4("503 user_unavailable", StubResponses.error(503, "subscription_sharing_user_unavailable")));
        for (int status : List.of(500, 502, 503, 504)) rows.add(row4(status + " without code", StubResponses.status(status, SECRET_BODY)));
        rows.add(row4("connection drop", new StubResponses.Reply(0, "", 0)));
        rows.add(row4("header timeout", StubResponses.delayed(StubResponses.completed("{}"), 1500)));
        // row 5
        for (int status : List.of(400, 404)) {
            rows.add(row5(status + " route_not_supported", StubResponses.error(status, "subscription_sharing_route_not_supported"),
                "subscription_sharing_route_not_supported"));
        }
        // row 6
        rows.add(row6("400 weird_new_code", StubResponses.error(400, "weird_new_code"), "weird_new_code"));
        rows.add(row6("64-character code", StubResponses.error(400, "a".repeat(64)), "a".repeat(64)));
        rows.add(row6("code with every allowed character", StubResponses.error(400, "Az09_.:-"), "Az09_.:-"));
        rows.add(row6("65-character code", StubResponses.error(400, "a".repeat(65)), "unknown_error"));
        rows.add(row6("code with a space", StubResponses.error(400, "bad code"), "unknown_error"));
        rows.add(row6("code with <", StubResponses.error(400, "bad<code"), "unknown_error"));
        rows.add(row6("code with a quote", StubResponses.error(400, "bad\"code"), "unknown_error"));
        for (int status : List.of(400, 404, 409, 422)) {
            rows.add(row6(status + " without code", StubResponses.status(status, "{}"), "http_" + status));
        }
        rows.add(row6("403 without code", StubResponses.status(403, SECRET_BODY), "http_403"));
        rows.add(row6("400 detail body", StubResponses.status(400, "{\"detail\":\"nope\"}"), "http_400"));
        rows.add(row6("400 non-JSON body", new StubResponses.Reply(400, "plain text PROVIDER-SECRET-BODY", 0, "text/plain", null, 0), "http_400"));
        return rows;
    }

    static Stream<Arguments> rowArgs() {
        return rows().stream().map(r -> Arguments.of(r.name(), r));
    }

    /** Models rows: the same classes (the capability rejection has no text.format to fall back from). */
    static Stream<Arguments> modelRowArgs() {
        List<Arguments> out = new ArrayList<>(rows().stream().map(r -> Arguments.of(r.name(), r)).toList());
        out.add(Arguments.of("400 unsupported_capability", row5("400 unsupported_capability",
            StubResponses.error(400, "subscription_sharing_unsupported_capability"), "subscription_sharing_unsupported_capability")));
        return out.stream();
    }

    private void assertNoTokenTraffic(int before) {
        assertThat(stub.requests).as("rows 1-7 never trigger a refresh, authorize or token request").hasSize(before);
        assertThat(stub.grant("refresh_token")).isEmpty();
    }

    // ---- a stage call (EVENT_NORMALIZATION) --------------------------------------------------------------

    @ParameterizedTest(name = "stage call: {0}")
    @MethodSource("rowArgs")
    void everyClassOfAStageCallEndsInItsTableRow(String name, Row row) throws Exception {
        gdeltArticles(v4());
        script(CLASSIFICATION, C_V4);
        always(NORMALIZATION, row.reply());
        String sid = connectedSid();
        int tokenRequests = stub.requests.size();
        Ran r = runWith(sid, A);
        assertFailure(r.run(), row.code(), row.message(), row.providerCode());
        assertThat(r.run().get("stage")).as("failed at its current stage").isEqualTo("CONNECTING_SIGNALS");
        assertThat(requests(NORMALIZATION)).as(name + ": requests").hasSize(row.requests());
        assertThat(requests(CLASSIFICATION)).isEmpty();
        assertThat(eventRows(r.id())).isEqualTo(0);
        assertFailureHygiene(r.run(), sid, r.id());
        assertThat(connectionState(sid)).as(name + ": connection state").isEqualTo(row.state());
        assertNoTokenTraffic(tokenRequests);
        if (row.requests() == 3) {
            List<StubResponses.Exchange> ex = responses.exchanges.stream().filter(e -> NORMALIZATION.equals(e.purpose)).toList();
            long gap1 = (ex.get(1).arrivedNanos - ex.get(0).arrivedNanos) / 1_000_000;
            long gap2 = (ex.get(2).arrivedNanos - ex.get(1).arrivedNanos) / 1_000_000;
            assertThat(gap1).as("first backoff = retry-delay").isGreaterThanOrEqualTo(RETRY_DELAY_MS - 5);
            assertThat(gap2).as("second backoff = 2 x retry-delay").isGreaterThanOrEqualTo(2 * RETRY_DELAY_MS - 5);
        }
        if ("SESSION_EXPIRED".equals(row.state())) {
            // credentials were dropped: the next start is refused without any token request
            assertThat(startRun(sid, B).andReturn().getResponse().getStatus()).isEqualTo(401);
            assertNoTokenTraffic(tokenRequests);
        }
    }

    // ---- GET /models -----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "GET /models: {0}")
    @MethodSource("modelRowArgs")
    void everyClassOfTheCatalogueCallEndsInItsTableRow(String name, Row row) throws Exception {
        responses.modelsResponder = req -> row.reply();
        String sid = connectedSid();
        int tokenRequests = stub.requests.size();
        Ran r = runWith(sid, A);
        assertFailure(r.run(), row.code(), row.message(), row.providerCode());
        assertThat(responses.modelRequests).as(name + ": GET /models requests").hasSize(row.requests());
        assertThat(responses.requests).as("no Responses call without a model").isEmpty();
        assertFailureHygiene(r.run(), sid, r.id());
        assertThat(connectionState(sid)).isEqualTo(row.state());
        assertNoTokenTraffic(tokenRequests);
        if (row.requests() == 3) {
            long gap1 = (responses.modelRequests.get(1).arrivedNanos() - responses.modelRequests.get(0).arrivedNanos()) / 1_000_000;
            long gap2 = (responses.modelRequests.get(2).arrivedNanos() - responses.modelRequests.get(1).arrivedNanos()) / 1_000_000;
            assertThat(gap1).isGreaterThanOrEqualTo(RETRY_DELAY_MS - 5);
            assertThat(gap2).isGreaterThanOrEqualTo(2 * RETRY_DELAY_MS - 5);
        }
    }

    // ---- retried calls that then succeed ---------------------------------------------------------------------

    @ParameterizedTest(name = "{0} transient failures then success")
    @MethodSource("transientCounts")
    void aTransientFailureThatClearsContinuesTheRun(int failures) throws Exception {
        gdeltArticles(v4());
        script(CLASSIFICATION, C_V4);
        List<StubResponses.Reply> replies = new ArrayList<>();
        for (int i = 0; i < failures; i++) replies.add(StubResponses.error(503, "subscription_sharing_usage_unavailable"));
        replies.add(StubResponses.completed(N_V4));
        scriptReplies(NORMALIZATION, replies.toArray(new StubResponses.Reply[0]));
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(requests(NORMALIZATION)).hasSize(failures + 1);
        assertThat(events(r)).hasSize(2);
        assertThat(responses.modelRequests).as("the catalogue is read once").hasSize(1);
    }

    static Stream<Arguments> transientCounts() {
        return Stream.of(Arguments.of(1), Arguments.of(2));
    }

    @ParameterizedTest(name = "GET /models {0} transient failures then success")
    @MethodSource("transientCounts")
    void aTransientCatalogueFailureThatClearsContinuesTheRun(int failures) throws Exception {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        responses.modelsResponder = req -> n.getAndIncrement() < failures
            ? StubResponses.error(503, "subscription_sharing_usage_unavailable")
            : new StubResponses.Reply(200, StubResponses.DEFAULT_CATALOGUE, 0);
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(responses.modelRequests).hasSize(failures + 1);
    }

    @Test
    void theRetryIsNotCountedAsASecondModelCallRowButSendsTheIdenticalBody() throws Exception {
        gdeltArticles(v4());
        script(CLASSIFICATION, C_V4);
        scriptReplies(NORMALIZATION, StubResponses.status(500, SECRET_BODY), StubResponses.status(500, SECRET_BODY),
            StubResponses.completed(N_V4));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<StubResponses.Request> calls = requests(NORMALIZATION);
        assertThat(calls).hasSize(3);
        assertThat(calls.get(1).body()).isEqualTo(calls.get(0).body());
        assertThat(calls.get(2).body()).isEqualTo(calls.get(0).body());
        assertThat(jdbc.queryForObject("select count(*) from model_call where run_id = cast(? as uuid) and purpose = 'EVENT_NORMALIZATION'",
            Integer.class, r.id())).as("event calls write no model_call row; at most one, never one per attempt").isLessThanOrEqualTo(1);
    }

    // ---- the three error channels of an answer that is a stream ------------------------------------------------

    private static final String[] CHANNELS = {"response.failed", "error event", "error event with error.code", "200 JSON failed"};

    private static StubResponses.Reply viaChannel(String channel, String code) {
        return switch (channel) {
            case "response.failed" -> StubResponses.sse(StubResponses.createdEvent(), StubResponses.failedEvent(code));
            case "error event" -> StubResponses.sse(StubResponses.createdEvent(), StubResponses.errorEvent(code));
            case "error event with error.code" -> StubResponses.sse(StubResponses.createdEvent(),
                "{\"type\":\"error\",\"error\":{\"code\":" + StubResponses.jsonString(code) + ",\"message\":\"stub\"}}");
            default -> new StubResponses.Reply(200, "{\"id\":\"resp_1\",\"status\":\"failed\",\"error\":{\"code\":"
                + StubResponses.jsonString(code) + ",\"message\":\"stub\"}}", 0);
        };
    }

    static Stream<Arguments> channelRows() {
        List<Arguments> out = new ArrayList<>();
        for (String channel : CHANNELS) {
            out.add(Arguments.of(channel, "subscription_sharing_invalid_user", row1("invalid_user", null)));
            out.add(Arguments.of(channel, "subscription_sharing_user_not_eligible", row2("not_eligible", null)));
            out.add(Arguments.of(channel, "subscription_sharing_usage_limit_exceeded", row3("usage_limit", null)));
            out.add(Arguments.of(channel, "subscription_sharing_usage_unavailable", row4("usage_unavailable", null)));
            out.add(Arguments.of(channel, "subscription_sharing_route_not_supported",
                row5("route_not_supported", null, "subscription_sharing_route_not_supported")));
            out.add(Arguments.of(channel, "weird_new_code", row6("weird", null, "weird_new_code")));
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("channelRows")
    void aFailureInsideTheStreamIsClassifiedLikeAnHttpError(String channel, String providerCode, Row row) throws Exception {
        gdeltArticles(v4());
        script(CLASSIFICATION, C_V4);
        always(NORMALIZATION, viaChannel(channel, providerCode));
        String sid = connectedSid();
        int tokenRequests = stub.requests.size();
        Ran r = runWith(sid, A);
        assertFailure(r.run(), row.code(), row.message(), row.providerCode());
        assertThat(requests(NORMALIZATION)).as(channel + " " + providerCode).hasSize(row.requests());
        assertFailureHygiene(r.run(), sid, r.id());
        assertThat(connectionState(sid)).isEqualTo(row.state());
        assertNoTokenTraffic(tokenRequests);
    }

    // ---- query expansion keeps its own rule ---------------------------------------------------------------------

    static Stream<Arguments> expansionFatal() {
        return Stream.of(
            Arguments.of("401", StubResponses.status(401, "{}"), "CHATGPT_SESSION_EXPIRED", M_EXPIRED),
            Arguments.of("invalid_user", StubResponses.error(400, "subscription_sharing_invalid_user"), "CHATGPT_SESSION_EXPIRED", M_EXPIRED),
            Arguments.of("not eligible", StubResponses.error(403, "subscription_sharing_user_not_eligible"), "CHATGPT_PLAN_NOT_ELIGIBLE", M_NOT_ELIGIBLE));
    }

    @ParameterizedTest(name = "expansion {0} fails the run at RESEARCH_STRATEGY")
    @MethodSource("expansionFatal")
    void sessionAndEligibilityFailuresOfTheExpansionFailTheRun(String name, StubResponses.Reply reply, String code, String message) throws Exception {
        responses.responder = req -> reply;
        Ran r = run(A);
        assertFailure(r.run(), code, message, null);
        assertThat(r.run().get("stage")).isEqualTo("RESEARCH_STRATEGY");
        assertThat(responses.requests).as("one attempt").hasSize(1);
        assertThat(gdelt.requests).isEmpty();
    }

    static Stream<Arguments> expansionFallback() {
        return Stream.of(
            Arguments.of("429 usage limit", StubResponses.error(429, "subscription_sharing_usage_limit_exceeded")),
            Arguments.of("503 unavailable (never retried)", StubResponses.error(503, "subscription_sharing_usage_unavailable")),
            Arguments.of("500 without code", StubResponses.status(500, "{}")),
            Arguments.of("unknown code", StubResponses.error(400, "weird_new_code")),
            Arguments.of("route not supported", StubResponses.error(400, "subscription_sharing_route_not_supported")),
            Arguments.of("403 without code", StubResponses.status(403, "{}")),
            Arguments.of("response.incomplete", StubResponses.sse(StubResponses.createdEvent(), StubResponses.incompleteEvent())),
            Arguments.of("response.failed with a code", StubResponses.sse(StubResponses.createdEvent(), StubResponses.failedEvent("weird_new_code"))));
    }

    @ParameterizedTest(name = "expansion {0} falls back to templates")
    @MethodSource("expansionFallback")
    void anyOtherFailureOfTheExpansionFallsBackToTemplatesWithOneAttempt(String name, StubResponses.Reply reply) throws Exception {
        // run-control.md FR-47: the empty-news run goes on speculatively, so only the expansion gets the failure
        var fallback = responses.defaultResponder();
        responses.responder = req -> EXPANSION.equals(StubResponses.purpose(req)) ? reply : fallback.apply(req);
        Ran r = run(A);
        assertThat(r.run().get("status")).as(name + ": " + r.run()).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
        assertThat(plan.get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(requests(EXPANSION)).as("query expansion is never retried").hasSize(1);
    }
}
