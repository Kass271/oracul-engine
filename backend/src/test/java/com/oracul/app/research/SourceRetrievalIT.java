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
// @trace FR-13
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

    // #8
    @Test
    void everyQueryIsSentToGdeltWithExactlyTheSpecifiedParameters() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        assertThat(gdelt.requests).hasSize(20);
        List<String> planTexts = new ArrayList<>();
        for (Map<String, Object> q : list(plan(researchBody(sid, id)).get("queries"))) planTexts.add((String) q.get("text"));
        List<String> sent = new ArrayList<>();
        for (StubGdelt.Request r : gdelt.requests) {
            assertThat(r.params().keySet()).containsExactlyInAnyOrder("query", "mode", "format", "maxrecords", "sort", "timespan");
            assertThat(r.params().get("mode")).isEqualTo("ArtList");
            assertThat(r.params().get("format")).isEqualTo("json");
            assertThat(r.params().get("maxrecords")).isEqualTo("25");
            assertThat(r.params().get("sort")).isEqualTo("HybridRel");
            assertThat(r.params().get("timespan")).isEqualTo("3months");
            assertThat(r.params().get("query")).endsWith(" sourcelang:english");
            assertThat(r.rawQuery()).as("parameters are URL-encoded").doesNotContain(" ");
            sent.add(r.params().get("query").substring(0, r.params().get("query").length() - " sourcelang:english".length()));
        }
        assertThat(sent).containsExactlyInAnyOrderElementsOf(planTexts);
    }

    // #8 timespan mapping
    @ParameterizedTest(name = "horizon {0} -> timespan {1}")
    @CsvSource({"1d,7d", "1w,7d", "1m,14d", "1y,3months", "5y,3months", "10y,3months", "20y,3months"})
    void timespanFollowsTheHorizon(String horizon, String timespan) throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = runToTerminal(sid, withHorizon(B, horizon));
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(gdelt.requests).hasSize(20);
        assertThat(gdelt.requests).allSatisfy(r -> assertThat(r.params().get("timespan")).isEqualTo(timespan));
    }

    // #8 + FR-13 rules: sources keep the topic of their first query and provider order
    @Test
    void sourcesCarryTheTopicOfTheirFirstQueryInQueryOrder() throws Exception {
        gdelt.responder = req -> StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/t" + req.number(), "t" + req.number()))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        Map<String, Object> plan = plan(researchBody(sid, id));
        Map<String, Map<String, Object>> intents = new HashMap<>();
        for (Map<String, Object> i : list(plan.get("intents"))) intents.put((String) i.get("id"), i);
        List<Map<String, Object>> queries = list(plan.get("queries"));
        assertThat(queries).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("OK");
            assertThat(q.get("articlesReturned")).isEqualTo(1);
        });
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(20);
        for (int i = 0; i < 20; i++) {
            Map<String, Object> s = sources.get(i);
            assertThat(s.get("id")).isEqualTo(String.format("S%03d", i + 1));
            assertThat(s.get("queryIds")).as(s.get("id") + " queryIds").isEqualTo(List.of(String.format("Q%02d", i + 1)));
            Map<String, Object> intent = intents.get((String) queries.get(i).get("intentId"));
            String expected = switch ((String) intent.get("bucket")) {
                case "WILDCARD" -> (String) intent.get("topicKey");
                case "ADJACENT" -> intent.get("category") == null ? "general" : (String) intent.get("category");
                case "MAJOR" -> "major";
                default -> "unexpected";
            };
            assertThat(s.get("topic")).as(s.get("id") + " topic").isEqualTo(expected);
        }
        Map<String, Object> run = json(getRun(sid, id));
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS
            .replace("\"searches\":0", "\"searches\":20").replace("\"articlesRetrieved\":0", "\"articlesRetrieved\":20")
            .replace("\"articlesConsidered\":0", "\"articlesConsidered\":20")
            .replace("\"uniqueEvents\":0", "\"uniqueEvents\":20")));
    }

    // #11
    @Test
    void failedQueriesDoNotStopTheRun() throws Exception {
        gdelt.responder = req -> req.number() <= 5 ? StubGdelt.status(503)
            : StubGdelt.json(StubGdelt.articles(List.of(article(gdelt.baseUrl() + "/articles/n" + req.number(), "n" + req.number()))));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(20);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(15);
        assertThat(counts.get("articlesConsidered")).isEqualTo(15);
        List<Map<String, Object>> queries = list(plan(researchBody(sid, id)).get("queries"));
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status"))).count()).isEqualTo(5);
        assertThat(queries.stream().filter(q -> "OK".equals(q.get("status"))).count()).isEqualTo(15);
        assertThat(queries.stream().filter(q -> "FAILED".equals(q.get("status")))).allSatisfy(q -> assertThat(q.get("articlesReturned")).isEqualTo(0));
        assertThat(sourceItems(sid, id)).hasSize(15);
    }

    static Stream<Arguments> unavailableProviders() {
        return Stream.of(
            Arguments.of("HTTP 503", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.status(503)),
            Arguments.of("connection dropped", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.drop()),
            Arguments.of("200 text/plain error text", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> new StubGdelt.Reply(200,
                "text/plain", "Your search contained a phrase that is too short", 0)),
            Arguments.of("200 invalid JSON", (Function<StubGdelt.Request, StubGdelt.Reply>) r -> StubGdelt.json("{not json")));
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

    // #13
    @Test
    void emptyAnswersEverywhereAreNotAFailure() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS.replace("\"searches\":0", "\"searches\":20")));
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
