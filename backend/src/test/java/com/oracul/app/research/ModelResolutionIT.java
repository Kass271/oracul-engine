package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * phase-02 chatgpt-inference.md FR-38 step 1: the model of a run is resolved once from GET /v1/models. The preferred
 * model (oracul.openai.model = stub-model in tests) wins at any visibility; catalogue classes are walked exhaustively.
 */
// @trace FR-38
class ModelResolutionIT extends AbstractPlanUsageIT {

    static final String P = "stub-model";

    private static String[] e(String slug, String visibility) {
        return new String[] {slug, visibility};
    }

    static Stream<Arguments> acceptedCatalogues() {
        return Stream.of(
            Arguments.of("[P(list)]", StubResponses.catalogue(e(P, "list")), P),
            Arguments.of("[P(hide)]", StubResponses.catalogue(e(P, "hide")), P),
            Arguments.of("[A(list), P(hide)]: preferred wins at any visibility",
                StubResponses.catalogue(e("model-a", "list"), e(P, "hide")), P),
            Arguments.of("[A(hide), P(list)]", StubResponses.catalogue(e("model-a", "hide"), e(P, "list")), P),
            Arguments.of("[A(hide), B(list), C(list)]: first listed",
                StubResponses.catalogue(e("model-a", "hide"), e("model-b", "list"), e("model-c", "list")), "model-b"),
            Arguments.of("[A(list), B(list)]", StubResponses.catalogue(e("model-a", "list"), e("model-b", "list")), "model-a"),
            Arguments.of("[A(hide), B(hide)]: first with a slug",
                StubResponses.catalogue(e("model-a", "hide"), e("model-b", "hide")), "model-a"),
            Arguments.of("[{slug:\"\"}, {slug:null}, B(hide)]",
                StubResponses.catalogue(e("", "list"), e(null, "list"), e("model-b", "hide")), "model-b"),
            Arguments.of("slug of 1 character", StubResponses.catalogue(e("x", "list")), "x"),
            Arguments.of("slug of 128 characters", StubResponses.catalogue(e("s".repeat(128), "list")), "s".repeat(128)),
            Arguments.of("slug of 129 characters is no slug: B(hide) is taken",
                StubResponses.catalogue(e("s".repeat(129), "list"), e("model-b", "hide")), "model-b"));
    }

    static Stream<Arguments> emptyCatalogues() {
        return Stream.of(
            Arguments.of("{\"models\":[]}", new StubResponses.Reply(200, "{\"models\":[]}", 0)),
            Arguments.of("{\"models\":null}", new StubResponses.Reply(200, "{\"models\":null}", 0)),
            Arguments.of("{}", new StubResponses.Reply(200, "{}", 0)),
            Arguments.of("[{slug:\"\"}]", StubResponses.catalogue(e("", "list"))),
            Arguments.of("[{slug:null}]", StubResponses.catalogue(e(null, "list"))),
            Arguments.of("only a 129-character slug", StubResponses.catalogue(e("s".repeat(129), "list"))));
    }

    // exhaustive over the catalogue classes of the spec
    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("acceptedCatalogues")
    void theChosenSlugIsSentInEveryCallAndStoredOnTheRun(String name, StubResponses.Reply catalogue, String expected) throws Exception {
        responses.modelsResponder = req -> catalogue;
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertThat(responses.modelRequests).as("one GET /models per run").hasSize(1);
        assertThat(responses.requests.size()).isGreaterThanOrEqualTo(5);
        for (StubResponses.Request req : responses.requests) {
            assertThat(at(req.body(), "$.model")).as("model of a " + StubResponses.purpose(req) + " call").isEqualTo(expected);
        }
        assertThat(jdbc.queryForObject("select model from generation_run where id = cast(? as uuid)", String.class, r.id()))
            .isEqualTo(expected);
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result(r).get("metadata");
        assertThat(meta.get("model")).as("metadata.model").isEqualTo(expected);
    }

    // exhaustive over the "no usable slug" classes
    @ParameterizedTest(name = "no model: {0}")
    @MethodSource("emptyCatalogues")
    void aCatalogueWithoutASlugFailsTheRunBeforeAnyResponsesCall(String name, StubResponses.Reply catalogue) throws Exception {
        responses.modelsResponder = req -> catalogue;
        Ran r = run(A);
        assertFailure(r.run(), "CHATGPT_NO_MODEL", M_NO_MODEL, null);
        assertThat(responses.requests).as("0 Responses calls").isEmpty();
        assertThat(responses.modelRequests).as("a 2xx catalogue is not retried").hasSize(1);
        assertThat(jdbc.queryForObject("select model from generation_run where id = cast(? as uuid)", String.class, r.id())).isNull();
        assertFailureHygiene(r.run(), r.sid(), r.id());
        assertThat(connectionState(r.sid())).isEqualTo("CONNECTED");
        assertThat(startRun(r.sid(), B).andReturn().getResponse().getStatus()).as("slot released").isEqualTo(202);
    }

    @Test
    void theCatalogueIsReadWithTheBearerTokenBeforeTheFirstResponsesCall() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        StubResponses.ModelsRequest models = responses.modelRequests.get(0);
        assertThat(models.headers().get("authorization")).startsWith("Bearer at-STUBSECRET-");
        assertThat(models.headers().get("authorization")).isEqualTo(responses.requests.get(0).headers().get("authorization"));
        assertThat(models.arrivedNanos()).as("GET /models comes first").isLessThan(responses.exchanges.get(0).arrivedNanos);
        assertThat(responses.exchanges.get(0).purpose).isEqualTo("QUERY_EXPANSION");
    }

    @Test
    void theModelRequestIsNotCountedAsAResponsesRequest() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertThat(purposes()).containsExactly(EXPANSION, NORMALIZATION, CLASSIFICATION, GEN, "SCENARIO_CRITIC", STORY);
    }

    @Test
    void theModelIsResolvedOncePerRunAndTheSameSlugServesRegenerationsAndCorrections() throws Exception {
        responses.modelsResponder = req -> StubResponses.catalogue(e("model-b", "list"), e("model-c", "list"));
        // the critic fails once: a regeneration; then everything passes
        scriptCritic(cr("CR-ICS"), cr("CR-PASS"));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertThat(requests(GEN)).as("initial + critic regeneration").hasSize(2);
        assertThat(responses.modelRequests).hasSize(1);
        for (StubResponses.Request req : responses.requests) {
            assertThat(at(req.body(), "$.model")).isEqualTo("model-b");
        }
    }

    @Test
    void anAlternativeRunResolvesItsOwnModelOnceBeforeItsFirstCall() throws Exception {
        responses.modelsResponder = req -> StubResponses.catalogue(e("model-b", "list"));
        Ran parent = runV4(A);
        assertStoryCompleted(parent.run());
        assertThat(responses.modelRequests).hasSize(1);
        int postsBefore = responses.requests.size();
        MvcResult started = mvc.perform(post("/api/runs/" + parent.id() + "/alternatives")
            .cookie(new Cookie("ORACUL_SID", parent.sid()))).andReturn();
        assertThat(started.getResponse().getStatus()).as(started.getResponse().getContentAsString()).isEqualTo(202);
        String altId = (String) json(started.getResponse().getContentAsString()).get("id");
        awaitDone(parent.sid(), altId);
        assertThat(responses.modelRequests).as("one more GET /models for the alternative run").hasSize(2);
        List<StubResponses.Request> altPosts = responses.requests.subList(postsBefore, responses.requests.size());
        assertThat(altPosts).isNotEmpty();
        for (StubResponses.Request req : altPosts) assertThat(at(req.body(), "$.model")).isEqualTo("model-b");
        assertThat(jdbc.queryForObject("select model from generation_run where id = cast(? as uuid)", String.class, altId))
            .isEqualTo("model-b");
        assertThat(responses.modelRequests.get(1).arrivedNanos())
            .isLessThan(responses.exchanges.get(postsBefore).arrivedNanos);
    }

    @Test
    void aRunWithoutSourcesStillStoresItsResolvedModel() throws Exception {
        Ran r = run(A); // default empty news feed: the run completes after the query expansion
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select model from generation_run where id = cast(? as uuid)", String.class, r.id()))
            .isEqualTo(P);
    }

    private static Answer cr(String name) {
        return text(StubResponses.criticFixture(name));
    }

    /** The 1st, 2nd... SCENARIO_CRITIC request is answered by these; later ones get CR-PASS. */
    private void scriptCritic(Answer... answers) {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Function<StubResponses.Request, StubResponses.Reply> fallback = responses.defaultResponder();
        always("SCENARIO_CRITIC", req -> {
            int k = n.getAndIncrement();
            return k < answers.length ? answers[k].apply(req) : fallback.apply(req);
        });
    }
}
