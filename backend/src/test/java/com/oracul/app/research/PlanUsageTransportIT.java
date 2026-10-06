package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * phase-02 chatgpt-inference.md FR-38 steps 2-4: the documented plan-usage transport. Every POST /v1/responses of a run
 * carries store:false, stream:true and the SSE Accept header; the answer is read as a Server-Sent-Events stream and
 * counts as success only on response.completed; text.format rejections fall back once.
 */
// @trace FR-38
class PlanUsageTransportIT extends AbstractPlanUsageIT {

    private static final List<String> ALLOWED_KEYS = List.of("model", "instructions", "input", "text", "store", "stream");
    private static final List<String> UNSUPPORTED = List.of("background", "conversation", "max_output_tokens", "max_tool_calls",
        "metadata", "moderation", "prompt", "prompt_cache_retention", "safety_identifier", "temperature", "top_logprobs",
        "top_p", "truncation", "user", "previous_response_id");

    // ---- request invariants over every call of a whole run -------------------------------------------------

    @Test
    void everyCallOfARunIsAStreamedStoreFalseCallWithTheSseAcceptHeader() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertThat(purposes()).containsExactly(EXPANSION, NORMALIZATION, CLASSIFICATION, GEN, "SCENARIO_CRITIC", STORY);
        for (StubResponses.Request req : responses.requests) {
            String purpose = StubResponses.purpose(req);
            assertThat(req.headers().get("accept")).as(purpose + " Accept").isEqualTo("text/event-stream");
            assertThat(req.headers().get("content-type")).as(purpose + " Content-Type").startsWith("application/json");
            assertThat(req.headers().get("authorization")).as(purpose).startsWith("Bearer at-STUBSECRET-");
            Map<String, Object> body = json(req.body());
            assertThat(body.get("store")).as(purpose + " store").isEqualTo(false);
            assertThat(body.get("stream")).as(purpose + " stream").isEqualTo(true);
            assertThat(body.get("model")).as(purpose + " model").isEqualTo("stub-model");
            assertThat(body.keySet()).as(purpose + " keys").containsExactlyInAnyOrderElementsOf(ALLOWED_KEYS);
            for (String forbidden : UNSUPPORTED) assertThat(body.keySet()).as(purpose + " top-level keys").doesNotContain(forbidden);
            assertNoToolKeys(req);
            List<Object> roles = JsonPath.read(req.body(), "$.input[*].role");
            assertThat(roles).as(purpose + " input roles").isNotEmpty().doesNotContain("system");
        }
    }

    @Test
    void modelCallRowsKeepTheBodiesWithStreamTrueAndNeverTheHeaders() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        List<String> bodies = jdbc.queryForList("select cast(request_body as text) from model_call where run_id = cast(? as uuid)",
            String.class, r.id());
        assertThat(bodies).isNotEmpty();
        for (String b : bodies) {
            assertThat(JsonPath.<Object>read(b, "$.stream")).isEqualTo(true);
            assertThat(JsonPath.<Object>read(b, "$.store")).isEqualTo(false);
            assertThat(JsonPath.<Object>read(b, "$.model")).isEqualTo("stub-model");
            assertThat(b).doesNotContain("Bearer").doesNotContain("authorization").doesNotContain("STUBSECRET");
        }
    }

    // ---- SSE sequences that are successes (observed through the query expansion: MODEL vs TEMPLATE_FALLBACK) ----

    /** The text the expansion call must return: the default queries with a recognisable prefix. */
    private static String expansionText(String input) {
        List<String[]> pairs = new ArrayList<>();
        for (String[] p : StubResponses.defaultQueries(input)) pairs.add(new String[] {p[0], "sse " + p[1]});
        return StubResponses.queriesJson(pairs);
    }

    private static String[] thirds(String t) {
        int a = t.length() / 3;
        int b = 2 * t.length() / 3;
        return new String[] {t.substring(0, a), t.substring(a, b), t.substring(b)};
    }

    private static String ev(String type, String dataJson) {
        return "event: " + type + "\ndata: " + dataJson + "\n\n";
    }

    private static StubResponses.Reply raw(String body) {
        return new StubResponses.Reply(200, body, 0, "text/event-stream", null, 0);
    }

    private static String json2(String type) {
        return "{\"type\":\"" + type + "\",\"response\":{\"id\":\"resp_1\"}}";
    }

    static Stream<Arguments> successfulStreams() {
        Function<String, StubResponses.Reply> plain = t -> {
            String[] p = thirds(t);
            return StubResponses.sse(StubResponses.createdEvent(), StubResponses.deltaEvent(p[0]), StubResponses.deltaEvent(p[1]),
                StubResponses.deltaEvent(p[2]), StubResponses.completedEvent(t));
        };
        Function<String, StubResponses.Reply> emptyOutput = t -> {
            String[] p = thirds(t);
            return StubResponses.sse(StubResponses.createdEvent(), StubResponses.deltaEvent(p[0]), StubResponses.deltaEvent(p[1]),
                StubResponses.deltaEvent(p[2]), StubResponses.completedEmptyEvent());
        };
        Function<String, StubResponses.Reply> unknownTypes = t -> {
            String[] p = thirds(t);
            return StubResponses.sse(StubResponses.createdEvent(), json2("response.in_progress"),
                json2("response.output_item.added"), StubResponses.deltaEvent(p[0]), json2("response.content_part.added"),
                StubResponses.deltaEvent(p[1]), json2("response.some_future_event"), StubResponses.deltaEvent(p[2]),
                json2("response.output_text.done"), StubResponses.completedEvent(t));
        };
        Function<String, StubResponses.Reply> dataOnly = t -> raw("data: " + StubResponses.createdEvent() + "\n\n"
            + "data: " + StubResponses.deltaEvent("ignored-because-completed-has-output") + "\n\n"
            + "data: " + StubResponses.completedEvent(t) + "\n\n");
        Function<String, StubResponses.Reply> commentsAndKeepAlives = t -> raw(": keep-alive\n\n"
            + ev("response.created", StubResponses.createdEvent()) + ":\n\n\n\n" + ": another comment\n"
            + ev("response.completed", StubResponses.completedEvent(t)) + "\n\n");
        Function<String, StubResponses.Reply> crlf = t -> raw(
            ev("response.created", StubResponses.createdEvent()).replace("\n", "\r\n")
                + ev("response.completed", StubResponses.completedEvent(t)).replace("\n", "\r\n"));
        Function<String, StubResponses.Reply> splitReads = t -> {
            String all = ev("response.created", StubResponses.createdEvent()) + ev("response.output_text.delta", StubResponses.deltaEvent(thirds(t)[0]))
                + ev("response.completed", StubResponses.completedEvent(t));
            List<String> pieces = new ArrayList<>();
            int step = 17; // cuts inside "event:", inside "data:" and inside the JSON
            for (int i = 0; i < all.length(); i += step) pieces.add(all.substring(i, Math.min(all.length(), i + step)));
            return StubResponses.sseChunks(15, pieces.toArray(new String[0]));
        };
        Function<String, StubResponses.Reply> json200 = StubResponses::completed;
        Function<String, StubResponses.Reply> multiLineData = t -> raw("event: response.created\ndata: " + StubResponses.createdEvent()
            + "\n\nevent: response.completed\ndata: " + StubResponses.completedEvent(t) + "\n\n");
        return Stream.of(
            Arguments.of("created, deltas, completed(T)", plain),
            Arguments.of("completed with empty output: the deltas are the text", emptyOutput),
            Arguments.of("unknown event types interleaved are ignored", unknownTypes),
            Arguments.of("data lines without event lines", dataOnly),
            Arguments.of("comments and blank keep-alive lines", commentsAndKeepAlives),
            Arguments.of("CRLF line endings", crlf),
            Arguments.of("data lines split over several reads", splitReads),
            Arguments.of("200 application/json status completed", json200),
            Arguments.of("plain events with event: lines", multiLineData));
    }

    @ParameterizedTest(name = "success: {0}")
    @MethodSource("successfulStreams")
    void aCompletedStreamIsReadAsTheAnswer(String name, Function<String, StubResponses.Reply> stream) throws Exception {
        responses.responder = req -> {
            if (!"QUERY_EXPANSION".equals(StubResponses.purpose(req))) return responses.defaultResponder().apply(req);
            return stream.apply(expansionText(req.inputText()));
        };
        Ran r = run(A);
        assertThat(r.run().get("status")).as(name + ": " + r.run()).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
        assertThat(plan.get("expansionMode")).as(name).isEqualTo("MODEL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queries = (List<Map<String, Object>>) plan.get("queries");
        assertThat(queries).hasSize(20).allSatisfy(q -> assertThat((String) q.get("text")).startsWith("sse "));
        assertThat(requests(EXPANSION)).hasSize(1);
    }

    // ---- SSE sequences that end a stage call without an answer: CHATGPT_INCOMPLETE ---------------------------

    static Stream<Arguments> incompleteStreams() {
        return Stream.of(
            Arguments.of("created, delta, response.incomplete", StubResponses.sse(StubResponses.createdEvent(),
                StubResponses.deltaEvent("{\"events\":"), StubResponses.incompleteEvent())),
            Arguments.of("created, response.failed without a code", StubResponses.sse(StubResponses.createdEvent(),
                StubResponses.failedEvent(null))),
            Arguments.of("stream closed after deltas without completed", StubResponses.sse(StubResponses.createdEvent(),
                StubResponses.deltaEvent("{\"events\":"), StubResponses.deltaEvent("[]}"))),
            Arguments.of("stream cut inside a data line", StubResponses.sseChunks(10, "event: response.created\ndata: "
                + StubResponses.createdEvent() + "\n\n", "event: response.completed\ndata: {\"type\":\"response.comp")),
            Arguments.of("only response.created", StubResponses.sse(StubResponses.createdEvent())),
            Arguments.of("empty 200 event stream", raw("")),
            Arguments.of("200 application/json status incomplete",
                new StubResponses.Reply(200, "{\"id\":\"resp_1\",\"status\":\"incomplete\",\"output\":[]}", 0)),
            Arguments.of("200 application/json status failed without error.code",
                new StubResponses.Reply(200, "{\"id\":\"resp_1\",\"status\":\"failed\",\"error\":{\"message\":\"x\"}}", 0)),
            Arguments.of("unknown events only", StubResponses.sse(StubResponses.createdEvent(), json2("response.in_progress"))));
    }

    @ParameterizedTest(name = "incomplete: {0}")
    @MethodSource("incompleteStreams")
    void aStreamWithoutCompletedFailsTheStageWithoutPartialText(String name, StubResponses.Reply reply) throws Exception {
        newsArticles(v4());
        script(CLASSIFICATION, C_V4);
        always(NORMALIZATION, reply);
        Ran r = run(A);
        assertFailure(r.run(), "CHATGPT_INCOMPLETE", M_INCOMPLETE, null);
        assertThat(r.run().get("stage")).isEqualTo("CONNECTING_SIGNALS");
        assertThat(requests(NORMALIZATION)).as("no retry").hasSize(1);
        assertThat(requests(CLASSIFICATION)).isEmpty();
        assertThat(eventRows(r.id())).isEqualTo(0);
        assertFailureHygiene(r.run(), r.sid(), r.id());
    }

    @Test
    void aTruncatedStreamNeverUsesItsPartialDeltasAsTheAnswer() throws Exception {
        // the deltas alone would be a valid answer: without response.completed they must not be used
        newsArticles(v4());
        always(NORMALIZATION, req -> StubResponses.sse(StubResponses.createdEvent(),
            StubResponses.deltaEvent(StubResponses.defaultNormalization(req.inputText()))));
        Ran r = run(A);
        assertFailure(r.run(), "CHATGPT_INCOMPLETE", M_INCOMPLETE, null);
        assertThat(eventRows(r.id())).isEqualTo(0);
    }

    @Test
    void anIncompleteQueryExpansionFallsBackToTemplatesInsteadOfFailingTheRun() throws Exception {
        responses.responder = req -> "QUERY_EXPANSION".equals(StubResponses.purpose(req))
            ? StubResponses.sse(StubResponses.createdEvent(), StubResponses.deltaEvent("{\"queries\":"), StubResponses.incompleteEvent())
            : responses.defaultResponder().apply(req);
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
        assertThat(plan.get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(requests(EXPANSION)).as("query expansion is never retried").hasSize(1);
    }

    // ---- structured-output fallback ----------------------------------------------------------------------------

    private static final String SENTENCE =
        "Answer with exactly one JSON object and nothing else (no Markdown fence). It must validate against this JSON Schema: ";

    private static boolean hasTextFormat(StubResponses.Request req) {
        try {
            Object format = JsonPath.read(req.body(), "$.text.format");
            return format != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** text.format bodies are rejected; bodies without text get the normal answer of the purpose. */
    private void rejectTextFormat(String param) {
        Function<StubResponses.Request, StubResponses.Reply> normal = responses.defaultResponder();
        String body = param == null
            ? "{\"error\":{\"code\":\"subscription_sharing_unsupported_capability\",\"message\":\"stub\"}}"
            : "{\"error\":{\"code\":\"subscription_sharing_unsupported_capability\",\"message\":\"stub\",\"param\":\"" + param + "\"}}";
        responses.responder = req -> hasTextFormat(req) ? new StubResponses.Reply(400, body, 0) : normal.apply(req);
    }

    @Test
    void theFallbackRepeatsTheRejectedCallExactlyOnceAndLaterCallsStartWithoutTextFormat() throws Exception {
        rejectTextFormat("text.format");
        newsArticles(v4());
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<StubResponses.Request> all = responses.requests;
        // the first call (query expansion) was rejected once and repeated once: 2 requests, then no more rejected round trips
        assertThat(hasTextFormat(all.get(0))).as("first attempt carries text.format").isTrue();
        assertThat(StubResponses.purpose(all.get(1))).isEqualTo(EXPANSION);
        assertThat(hasTextFormat(all.get(1))).as("the repeat has no text").isFalse();
        assertThat(json(all.get(1).body())).doesNotContainKey("text");
        assertThat(all.stream().filter(PlanUsageTransportIT::hasTextFormat)).as("exactly one rejected round trip").hasSize(1);
        // the repeat: same input, instructions = original + the sentence and the removed schema
        String original = instructionsOf(all.get(0));
        String repeated = instructionsOf(all.get(1));
        assertThat(repeated).startsWith(original).contains(SENTENCE);
        String schemaJson = repeated.substring(repeated.indexOf(SENTENCE) + SENTENCE.length()).trim();
        Object schema = JsonPath.read(schemaJson, "$");
        assertThat(schema).isEqualTo(JsonPath.read(all.get(0).body(), "$.text.format.schema"));
        assertThat(all.get(1).inputText()).isEqualTo(all.get(0).inputText());
        // later calls of every stage go without text.format and with the instruction from the start
        for (StubResponses.Request req : all.subList(2, all.size())) {
            assertThat(json(req.body())).as(StubResponses.purpose(req)).doesNotContainKey("text");
            assertThat(instructionsOf(req)).as(StubResponses.purpose(req)).contains(SENTENCE);
            assertThat(JsonPath.<Object>read(req.body(), "$.stream")).isEqualTo(true);
        }
        assertThat(purposes().stream().filter(p -> !p.equals(EXPANSION)).distinct().toList())
            .contains(NORMALIZATION, CLASSIFICATION, GEN, "SCENARIO_CRITIC", STORY);
    }

    @Test
    void aFencedFallbackAnswerParsesLikeBareJson() throws Exception {
        for (String fence : List.of("```json\n", "```\n")) {
            news.reset();
            responses.reset();
            rejectTextFormat(null);
            Function<StubResponses.Request, StubResponses.Reply> inner = responses.responder;
            responses.responder = req -> {
                StubResponses.Reply reply = inner.apply(req);
                if (reply.status() != 200 || reply.contentType() != null) return reply;
                String text = JsonPath.read(reply.body(), "$.output[0].content[0].text");
                return StubResponses.completed(fence + text + "\n```");
            };
            newsArticles(v4());
            Ran r = run(A);
            assertThat(r.run().get("status")).as(fence + " run: " + r.run()).isEqualTo("COMPLETED");
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
            assertThat(plan.get("expansionMode")).as(fence).isEqualTo("MODEL");
            assertThat(events(r)).as(fence).isNotEmpty();
            resetStructuredOutputFlag();
        }
    }

    @Test
    void aBodyWithoutTextFormatIsNeverFallenBackAndAFailingFallbackIsARequestRejection() throws Exception {
        // every call is rejected, with or without text: the repeat is rejected too -> row 5
        newsArticles(v4());
        always(NORMALIZATION, StubResponses.error(400, "subscription_sharing_unsupported_capability"));
        script(CLASSIFICATION, C_V4);
        Ran r = run(A);
        assertFailure(r.run(), "CHATGPT_REQUEST_REJECTED", M_REJECTED, "subscription_sharing_unsupported_capability");
        assertThat(requests(NORMALIZATION)).as("the call and its single repeat").hasSize(2);
        assertThat(json(requests(NORMALIZATION).get(1).body())).doesNotContainKey("text");
        assertFailureHygiene(r.run(), r.sid(), r.id());
    }

    @Test
    void aRejectionNamingAnotherParameterIsNoFallback() throws Exception {
        newsArticles(v4());
        always(NORMALIZATION, new StubResponses.Reply(400,
            "{\"error\":{\"code\":\"subscription_sharing_unsupported_capability\",\"message\":\"stub\",\"param\":\"tools\"}}", 0));
        script(CLASSIFICATION, C_V4);
        Ran r = run(A);
        assertFailure(r.run(), "CHATGPT_REQUEST_REJECTED", M_REJECTED, "subscription_sharing_unsupported_capability");
        assertThat(requests(NORMALIZATION)).as("param tools: no fallback").hasSize(1);
        assertThat(hasTextFormat(requests(NORMALIZATION).get(0))).isTrue();
    }

    @Test
    void anAbsentParamStillTriggersTheFallback() throws Exception {
        rejectTextFormat(null);
        newsArticles(v4());
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(responses.requests.stream().filter(PlanUsageTransportIT::hasTextFormat)).hasSize(1);
    }

    @Test
    void aParamStartingWithTextTriggersTheFallback() throws Exception {
        rejectTextFormat("text");
        newsArticles(v4());
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(responses.requests.stream().filter(PlanUsageTransportIT::hasTextFormat)).hasSize(1);
    }

    @Test
    void anInvalidFallbackAnswerIsHandledLikeAnInvalidStructuredAnswer() throws Exception {
        // the fallback answer of the scenario stage is not JSON twice -> INVALID_SCENARIO after one correction retry
        rejectTextFormat("text.format");
        Function<StubResponses.Request, StubResponses.Reply> inner = responses.responder;
        responses.responder = req -> GEN.equals(StubResponses.purpose(req)) && !hasTextFormat(req)
            ? StubResponses.completed("this is not json") : inner.apply(req);
        newsArticles(v4());
        Ran r = run(A);
        assertFailure(r.run(), "INVALID_SCENARIO", INVALID, null);
        assertThat(requests(GEN).stream().filter(q -> !hasTextFormat(q)).count()).as("initial + one correction").isEqualTo(2);
    }
}
