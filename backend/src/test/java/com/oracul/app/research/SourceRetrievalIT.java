package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oracul.app.runs.AbstractRunIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Rows 8, 11-14 of research-pipeline.md "Slice 05_search-sources" integration tests (GDELT search stage). */
// @trace FR-13, FR-44
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=10",
})
class SourceRetrievalIT extends AbstractRunIT {

    static final String NEWS_DOWN = "ORACUL could not reach its news sources — try again later";

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> plan(Map<String, Object> research) {
        return (Map<String, Object>) research.get("searchPlan");
    }

    private Map<String, Object> runToTerminal(String sid, String body) throws Exception {
        String id = (String) startOk(sid, body).get("id");
        return awaitDone(sid, id);
    }

    private static String article(String url, String name) {
        return StubGdelt.article(url, "Title " + name, "reuters.com", "English",
            StubGdelt.seendate(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    // #8 (FR-44: 20 queries go out as 4 OR-group requests)
    @Test
    void theQueriesAreSentToGdeltAsFourOrGroupsWithExactlyTheSpecifiedParameters() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        assertThat(gdelt.requests).hasSize(4);
        List<String> planTexts = new ArrayList<>();
        for (Map<String, Object> q : list(plan(researchBody(sid, id)).get("queries"))) planTexts.add((String) q.get("text"));
        List<String> sent = new ArrayList<>();
        for (StubGdelt.Request r : gdelt.requests) {
            assertThat(r.params().keySet()).containsExactlyInAnyOrder("query", "mode", "format", "maxrecords", "sort", "timespan");
            assertThat(r.params().get("mode")).isEqualTo("ArtList");
            assertThat(r.params().get("format")).isEqualTo("json");
            assertThat(r.params().get("maxrecords")).as("25 x 5 elements").isEqualTo("125");
            assertThat(r.params().get("sort")).isEqualTo("HybridRel");
            assertThat(r.params().get("timespan")).isEqualTo("3months");
            assertThat(r.params().get("query")).matches("^\\(.+( OR .+)+\\) sourcelang:english$");
            assertThat(r.rawQuery()).as("parameters are URL-encoded").doesNotContain(" ");
            assertThat(r.elements()).hasSize(5);
            sent.addAll(r.elements());
        }
        assertThat(sent).as("plan order, contiguous groups").containsExactlyElementsOf(planTexts);
    }

    // #8 timespan mapping
    @ParameterizedTest(name = "horizon {0} -> timespan {1}")
    @CsvSource({"1d,7d", "1w,7d", "1m,14d", "1y,3months", "5y,3months", "10y,3months", "20y,3months"})
    void timespanFollowsTheHorizon(String horizon, String timespan) throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = runToTerminal(sid, withHorizon(B, horizon));
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(gdelt.requests).hasSize(4);
        assertThat(gdelt.requests).allSatisfy(r -> assertThat(r.params().get("timespan")).isEqualTo(timespan));
    }

    // #8 + FR-13 rules: sources keep the topic of their (attributed) query, in group then response order
    @Test
    void sourcesCarryTheTopicOfTheirAttributedQueryInGroupOrder() throws Exception {
        gdelt.responder = req -> StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/t" + req.number(), "t" + req.number()))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        assertThat(gdelt.requests).hasSize(4);
        Map<String, Object> plan = plan(researchBody(sid, id));
        Map<String, Map<String, Object>> intents = new HashMap<>();
        for (Map<String, Object> i : list(plan.get("intents"))) intents.put((String) i.get("id"), i);
        List<Map<String, Object>> queries = list(plan.get("queries"));
        // the titles share no token with any element: every article goes to the first element of its group (Q01, Q06, Q11, Q16)
        for (int i = 0; i < 20; i++) {
            boolean first = i % 5 == 0;
            assertThat(queries.get(i).get("status")).as("Q" + (i + 1)).isEqualTo(first ? "OK" : "EMPTY");
            assertThat(queries.get(i).get("articlesReturned")).as("Q" + (i + 1)).isEqualTo(first ? 1 : 0);
        }
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(4);
        for (int g = 0; g < 4; g++) {
            Map<String, Object> s = sources.get(g);
            int qi = g * 5;
            assertThat(s.get("id")).isEqualTo(String.format("S%03d", g + 1));
            assertThat(s.get("queryIds")).as(s.get("id") + " queryIds").isEqualTo(List.of(String.format("Q%02d", qi + 1)));
            Map<String, Object> intent = intents.get((String) queries.get(qi).get("intentId"));
            String expected = switch ((String) intent.get("bucket")) {
                case "WILDCARD" -> (String) intent.get("topicKey");
                case "ADJACENT" -> intent.get("category") == null ? "general" : (String) intent.get("category");
                case "MAJOR" -> "major";
                default -> "unexpected";
            };
            assertThat(s.get("topic")).as(s.get("id") + " topic").isEqualTo(expected);
        }
        Map<String, Object> run = json(getRun(sid, id));
        Map<String, Object> counts = castMap((Map<?, ?>) run.get("counts"));
        assertThat(counts.get("searches")).isEqualTo(20);
        assertThat(counts.get("articlesRetrieved")).as("4 answered groups x 1 article").isEqualTo(4);
        assertThat(counts.get("articlesConsidered")).isEqualTo(4);
        assertThat(counts.get("uniqueEvents")).isEqualTo(4);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    // #11 (FR-44: groups fail, not single queries)
    @Test
    void failedGroupsDoNotStopTheRun() throws Exception {
        gdelt.responder = req -> req.number() <= 2 ? StubGdelt.status(503)
            : StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/n" + req.number(), "n" + req.number()))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(20);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(2);
        assertThat(counts.get("articlesConsidered")).isEqualTo(2);
        assertThat(gdelt.requests).as("a 503 is never retried").hasSize(4);
        List<Map<String, Object>> queries = list(plan(researchBody(sid, id)).get("queries"));
        for (int i = 0; i < 20; i++) {
            String expected = i < 10 ? "FAILED" : i % 5 == 0 ? "OK" : "EMPTY";
            assertThat(queries.get(i).get("status")).as("Q" + (i + 1)).isEqualTo(expected);
            assertThat(queries.get(i).get("articlesReturned")).as("Q" + (i + 1)).isEqualTo(expected.equals("OK") ? 1 : 0);
        }
        assertThat(sourceItems(sid, id)).hasSize(2);
    }

    static Stream<Arguments> unavailableProviders() {
        return Stream.of(
            Arguments.of("HTTP 503", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.status(503)),
            Arguments.of("connection dropped", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.drop()),
            Arguments.of("200 text/plain error text", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> new StubGdelt.Reply(200,
                "text/plain", "Your search contained a phrase that is too short", 0)),
            Arguments.of("200 invalid JSON", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.json("{not json")),
            Arguments.of("HTTP 500", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.status(500)));
    }

    // #12
    @ParameterizedTest(name = "provider unavailable: {0}")
    @MethodSource("unavailableProviders")
    void everyQueryFailingEndsTheRunWithNewsUnavailable(String name, Function<StubGdelt.Request, StubGdelt.Reply> reply) throws Exception {
        gdelt.responder = reply;
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as(name).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"NEWS_UNAVAILABLE\",\"message\":\"" + NEWS_DOWN + "\"}"));
        assertThat(run.get("stage")).isEqualTo("SEARCHING");
        assertThat(run.get("stageIndex")).isEqualTo(3);
        assertThat(run.get("completedAt")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(20);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(0);
        assertThat(counts.get("articlesConsidered")).isEqualTo(0);
        assertThat(gdelt.requests).as("one request per group, none of these answers is retried").hasSize(4);
        List<Map<String, Object>> queries = list(plan(researchBody(sid, id)).get("queries"));
        assertThat(queries).hasSize(20).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("FAILED");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
        assertThat(json(getSources(sid, id))).isEqualTo(json("{\"items\":[]}"));
        assertThat(responses.requests).as("expansion only - no later ChatGPT call").hasSize(1);
        MvcResult second = startRun(sid, B).andReturn();
        assertThat(second.getResponse().getStatus()).as("slot released").isEqualTo(202);
        // let the follow-up run finish so its requests cannot leak into the next test/invocation
        awaitDone(sid, (String) json(second.getResponse().getContentAsString()).get("id"));
    }

    // FR-44 step 5: one 429 per group is retried once (rate-limit-wait PT0S in tests)
    @Test
    void aRateLimitedGroupIsRetriedOnceAndTheRunCompletes() throws Exception {
        java.util.concurrent.atomic.AtomicInteger limited = new java.util.concurrent.atomic.AtomicInteger();
        gdelt.responder = req -> limited.getAndIncrement() == 0
            ? new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds.", 0)
            : StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/r" + req.number(), "r" + req.number()))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(gdelt.requests).as("4 groups + 1 retry").hasSize(5);
        assertThat(gdelt.requests.get(1).params()).as("the retry is identical").isEqualTo(gdelt.requests.get(0).params());
        assertThat(gdelt.requests.get(1).elements()).isEqualTo(gdelt.requests.get(0).elements());
        List<Map<String, Object>> queries = list(plan(researchBody(sid, id)).get("queries"));
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status")))).isEmpty();
        assertThat(sourceItems(sid, id)).hasSize(4);
    }

    // FR-44 step 5: a second 429 fails the group; every group failing ends the run
    @Test
    void rateLimitedTwiceOnEveryGroupEndsTheRunWithNewsUnavailable() throws Exception {
        gdelt.responder = req -> new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds.", 0);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json("{\"code\":\"NEWS_UNAVAILABLE\",\"message\":\"" + NEWS_DOWN + "\"}"));
        assertThat(run.get("stage")).isEqualTo("SEARCHING");
        assertThat(run.get("stageIndex")).isEqualTo(3);
        assertThat(gdelt.requests).as("4 groups x (first attempt + one retry)").hasSize(8);
        assertThat(list(plan(researchBody(sid, id)).get("queries"))).hasSize(20)
            .allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
        assertThat(responses.requests).as("expansion only - no later ChatGPT call").hasSize(1);
    }

    // #13
    @Test
    void emptyAnswersEverywhereAreNotAFailure() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS.replace("\"searches\":0", "\"searches\":20")));
        assertThat(gdelt.requests).hasSize(4);
        assertThat(list(plan(researchBody(sid, id)).get("queries"))).hasSize(20).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("EMPTY");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
        assertThat(json(getSources(sid, id))).isEqualTo(json("{\"items\":[]}"));
    }

    // #13: the other EMPTY shapes
    @ParameterizedTest(name = "EMPTY answer: {0}")
    @CsvSource(delimiter = '|', value = {"empty articles|{\"articles\":[]}", "empty body|", "empty object|{}"})
    void emptyShapesAreEmptyNotFailed(String name, String body) throws Exception {
        gdelt.responder = req -> StubGdelt.json(body == null ? "" : body);
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as(name).isEqualTo("COMPLETED");
        assertThat(list(plan(researchBody(sid, id)).get("queries"))).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
    }

    // #14
    @Test
    void sourcesOfUnknownMalformedAndForeignRunsAre404() throws Exception {
        String x = connectedSid();
        String y = connectedSid();
        String id = (String) startOk(x, B).get("id");
        awaitDone(x, id);
        for (ResultActions r : List.of(getSources(x, NO_RUN), getSources(x, "abc"), getSources(y, id))) {
            try {
                r.andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.*", hasSize(2)))
                    .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Future not found"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    // #1-14 NFR-1: no token in sources
    @Test
    void noStubTokenReachesSourcesOrSearchPlanColumns() throws Exception {
        gdelt.responder = req -> StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/s" + req.number(), "s" + req.number()))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        awaitDone(sid, id);
        String sources = getSources(sid, id).andReturn().getResponse().getContentAsString();
        String rows = String.join(" ", jdbc.queryForList("select cast(to_jsonb(s) as text) from source s", String.class));
        String plan = jdbc.queryForObject("select cast(search_plan as text) from generation_run where id = cast(? as uuid)", String.class, id);
        assertThat(rows).isNotBlank();
        Set<String> issued = new HashSet<>(stub.issued);
        assertThat(issued).isNotEmpty();
        for (String t : issued) {
            assertThat(sources).doesNotContain(t);
            assertThat(rows).doesNotContain(t);
            assertThat(plan).doesNotContain(t);
        }
        assertThat(sources).doesNotContain("Authorization").doesNotContain("Bearer");
    }
}
