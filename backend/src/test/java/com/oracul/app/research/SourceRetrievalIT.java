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

/**
 * Rows 8, 11-14 of research-pipeline.md "Slice 05_search-sources" integration tests (search stage, Google News RSS), as
 * changed by FR-52 and FR-50: one request per planned query (body A: two pipelines x 3 queries = 6, body B: one GENERAL pipeline x 3),
 * the statuses live in {@code searchPlan.pipelines[].queries[]}, a 429 retried once (after a short rate-limit-wait here).
 */
// @trace FR-13, FR-44, FR-47, FR-49, FR-50, FR-52, FR-53
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=10",
    "oracul.news.google.rate-limit-wait=PT0.05S",
})
class SourceRetrievalIT extends AbstractRunIT {

    static final String NEWS_DOWN = "ORACUL could not reach its news sources — try again later";

    private Map<String, Object> runToTerminal(String sid, String body) throws Exception {
        String id = (String) startOk(sid, body).get("id");
        return awaitDone(sid, id);
    }

    /** One Google News item "Title <name>" whose link resolves (302) to /articles/<name>. */
    private String item(String name) {
        return StubNews.rssItem("Title " + name, news.baseUrl() + "/rss/articles/" + name,
            StubNews.pubDate(Instant.now().minus(1, ChronoUnit.DAYS)), "Reuters", "https://www.reuters.com");
    }

    // #8 (FR-52: 6 queries of body A go out as 6 bare requests)
    @Test
    void theQueriesAreSentToGoogleNewsOnePerQueryWithExactlyTheSpecifiedParameters() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per planned query").hasSize(6);
        List<String> planTexts = new ArrayList<>(PlanJson.queryTexts(researchBody(sid, id)));
        assertThat(planTexts).as("the queries of both pipelines").hasSize(6);
        List<String> sent = new ArrayList<>();
        for (StubNews.Request r : news.requests) {
            assertThat(r.params().keySet()).containsExactlyInAnyOrder("q", "hl", "gl", "ceid");
            assertThat(r.params().get("hl")).isEqualTo("en-US");
            assertThat(r.params().get("gl")).isEqualTo("US");
            assertThat(r.params().get("ceid")).isEqualTo("US:en");
            assertThat(r.q()).matches("^[^()\"]+ when:90d$");
            assertThat(r.q()).doesNotContain(" OR ");
            assertThat(r.rawQuery()).as("parameters are URL-encoded").doesNotContain(" ");
            assertThat(r.elements()).hasSize(1);
            sent.add(r.q());
        }
        assertThat(sent.stream().sorted().toList()).as("every planned query once, cleaned, plus the window")
            .containsExactlyElementsOf(planTexts.stream().map(t -> ParallelSearchSupport.text(t) + " when:90d").sorted().toList());
    }

    // #8 horizon -> when:<N>d
    @ParameterizedTest(name = "horizon {0} -> when:{1}d")
    @CsvSource({"1d,7", "1w,7", "1m,14", "1y,90", "5y,90", "10y,90", "20y,90"})
    void whenFollowsTheHorizon(String horizon, int days) throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = runToTerminal(sid, withHorizon(B, horizon));
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per planned query (body B: 3)").hasSize(3);
        assertThat(news.requests).allSatisfy(r -> assertThat(r.q()).endsWith(" when:" + days + "d"));
    }

    // #8 + FR-13 rules + FR-52 + FR-50 acceptance 5: every source carries the pipeline and the topic of the query whose
    // request returned it, in plan order
    @Test
    void sourcesCarryThePipelineAndTopicOfTheQueryThatReturnedThemInPlanOrder() throws Exception {
        news.responder = req -> StubNews.rss(item("t" + req.number()));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        assertThat(news.requests).hasSize(6);
        Map<String, Object> research = researchBody(sid, id);
        List<Map<String, Object>> pipelines = PlanJson.pipelines(research);
        assertThat(pipelines).hasSize(2);
        List<Map<String, Object>> queries = PlanJson.queries(research);
        assertThat(queries).hasSize(6);
        for (int i = 0; i < 6; i++) {
            assertThat(queries.get(i).get("status")).as("Q" + (i + 1)).isEqualTo("OK");
            assertThat(queries.get(i).get("articlesReturned")).as("Q" + (i + 1)).isEqualTo(1);
        }
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(6);
        for (int g = 0; g < 6; g++) {
            Map<String, Object> s = sources.get(g);
            Map<String, Object> pipeline = pipelines.get(g / 3);
            assertThat(s.get("id")).isEqualTo(String.format("S%03d", g + 1));
            assertThat(s.get("queryIds")).as(s.get("id") + " queryIds").isEqualTo(List.of(String.format("Q%02d", g + 1)));
            assertThat(s.get("pipelineIds")).as(s.get("id") + " pipelineIds").isEqualTo(List.of(pipeline.get("id")));
            assertThat(s.get("topic")).as(s.get("id") + " topic = topicKey of its pipeline").isEqualTo(pipeline.get("topicKey"));
        }
        assertThat(sources.subList(0, 3)).allSatisfy(s -> assertThat(s.get("topic")).isEqualTo("biology-new-pandemic"));
        assertThat(sources.subList(3, 6)).allSatisfy(s -> assertThat(s.get("topic")).isEqualTo("robotics-humanoid-boom"));
        Map<String, Object> run = json(getRun(sid, id));
        Map<String, Object> counts = castMap((Map<?, ?>) run.get("counts"));
        assertThat(counts.get("searches")).isEqualTo(6);
        assertThat(counts.get("articlesRetrieved")).as("6 answered queries x 1 article").isEqualTo(6);
        assertThat(counts.get("articlesConsidered")).isEqualTo(6);
        assertThat(counts.get("uniqueEvents")).isEqualTo(6);
        assertThat(counts.get("sourcesKept")).as("FR-53: one source per answered query, 3 per pipeline (<= 4)").isEqualTo(6);
        assertThat(pipelines.get(0).get("candidatesConsidered")).isEqualTo(3);
        assertThat(pipelines.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(pipelines.get(1).get("candidatesConsidered")).isEqualTo(3);
        assertThat(pipelines.get(1).get("sourceIds")).isEqualTo(List.of("S004", "S005", "S006"));
        assertThat(run.get("status")).isEqualTo("COMPLETED");
    }

    // FR-50 acceptance 5 (GENERAL): the single pipeline's sources have pipelineIds [W01] and the topic "major"
    @Test
    void aGeneralRunsSourcesBelongToW01AndHaveTheTopicMajor() throws Exception {
        news.responder = req -> StubNews.rss(item("g" + req.number()));
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(3);
        assertThat(sources).allSatisfy(s -> {
            assertThat(s.get("pipelineIds")).isEqualTo(List.of("W01"));
            assertThat(s.get("topic")).isEqualTo("major");
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    // #11 (FR-52: a failing request fails one query, not a group)
    @Test
    void failedQueriesDoNotStopTheRun() throws Exception {
        news.responder = req -> req.number() == 1 ? StubNews.status(503)
            : StubNews.rss(item("n" + req.number()));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(6);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(5);
        assertThat(counts.get("articlesConsidered")).isEqualTo(5);
        assertThat(counts.get("sourcesKept")).isEqualTo(5);
        assertThat(news.requests).as("a 503 is never retried: one request per query").hasSize(6);
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).hasSize(6);
        for (int i = 0; i < 6; i++) {
            String expected = i < 1 ? "FAILED" : "OK";
            assertThat(queries.get(i).get("status")).as("Q" + (i + 1)).isEqualTo(expected);
            assertThat(queries.get(i).get("articlesReturned")).as("Q" + (i + 1)).isEqualTo(expected.equals("OK") ? 1 : 0);
        }
        assertThat(sourceItems(sid, id)).hasSize(5);
        // FR-53: a FAILED query contributes no candidates; W01 keeps the 2 of Q02 / Q03, W02 all 3
        List<Map<String, Object>> pipelines = PlanJson.pipelines(researchBody(sid, id));
        assertThat(pipelines.get(0).get("candidatesConsidered")).isEqualTo(2);
        assertThat(pipelines.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002"));
        assertThat(pipelines.get(1).get("candidatesConsidered")).isEqualTo(3);
        assertThat(pipelines.get(1).get("sourceIds")).isEqualTo(List.of("S003", "S004", "S005"));
    }

    static Stream<Arguments> unavailableProviders() {
        return Stream.of(
            Arguments.of("HTTP 503", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(503)),
            Arguments.of("connection dropped", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.drop()),
            Arguments.of("200 text/plain error text", (Function<StubNews.Request, StubNews.Reply>) r -> new StubNews.Reply(200,
                "text/plain", "Your search contained a phrase that is too short", 0)),
            Arguments.of("200 invalid XML", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.rssBody("<rss><channel>{not xml")),
            Arguments.of("HTTP 500", (Function<StubNews.Request, StubNews.Reply>) r -> StubNews.status(500)));
    }

    // #12
    @ParameterizedTest(name = "provider unavailable: {0}")
    @MethodSource("unavailableProviders")
    void everyQueryFailingNoLongerEndsTheRun(String name, Function<StubNews.Request, StubNews.Reply> reply) throws Exception {
        news.responder = reply;
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        // run-control.md FR-47: every group FAILED is no failure; the run continues speculatively with 0 sources
        assertThat(run.get("status")).as(name + ": " + run).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(run.get("headline")).isNotNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(6);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(0);
        assertThat(counts.get("articlesConsidered")).isEqualTo(0);
        assertThat(counts.get("sourcesKept")).as("FR-53: also 0 is committed").isEqualTo(0);
        assertThat(news.requests).as("one request per query, none of these answers is retried, no fallback").hasSize(6);
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).hasSize(6).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("FAILED");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
        assertThat(json(getSources(sid, id))).isEqualTo(json("{\"items\":[]}"));
        assertThat(responses.requests.stream().map(StubResponses::purpose).toList())
            .as("no event stages, but the speculative scenario, its critic and the story")
            .containsExactly("QUERY_GENERATION", "QUERY_GENERATION", "SCENARIO_GENERATION", "SCENARIO_CRITIC", "STORY_WRITING");
        MvcResult second = startRun(sid, B).andReturn();
        assertThat(second.getResponse().getStatus()).as("slot released").isEqualTo(202);
        // let the follow-up run finish so its requests cannot leak into the next test/invocation
        awaitDone(sid, (String) json(second.getResponse().getContentAsString()).get("id"));
    }

    // FR-52: a 429 is retried once after rate-limit-wait with the identical request
    @Test
    void aRateLimitedQueryIsRetriedOnceAndTheRunCompletes() throws Exception {
        news.responder = req -> req.number() == 1
            ? new StubNews.Reply(429, "text/plain", "Too Many Requests", 0)
            : StubNews.rss(item("r" + req.number()));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(news.requests).as("6 queries + 1 retry").hasSize(7);
        // the retry comes after rate-limit-wait and a fresh permit, so not necessarily right after the 429: find it by content
        String limited = news.requests.get(0).rawQuery();
        assertThat(news.requests.stream().filter(r -> r.rawQuery().equals(limited)).count())
            .as("the 429'd request plus exactly one identical retry").isEqualTo(2);
        assertThat(news.requests.stream().map(StubNews.Request::rawQuery).distinct().count())
            .as("every other request is sent once").isEqualTo(6);
        assertThat(news.paths.stream().filter(p -> p.equals("/rss/search")).count()).isEqualTo(7);
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).hasSize(6);
        for (int i = 0; i < 6; i++) {
            assertThat(queries.get(i).get("status")).as("Q" + (i + 1)).isEqualTo("OK");
        }
        assertThat(sourceItems(sid, id)).hasSize(6);
    }

    // a second 429 fails the query after exactly 2 requests, never the run (run-control.md FR-47)
    @Test
    void rateLimitedTwiceOnEveryQueryFailsTheQueriesButNotTheRun() throws Exception {
        news.responder = req -> new StubNews.Reply(429, "text/plain", "Too Many Requests", 0);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(news.requests).as("6 queries, two requests each").hasSize(12);
        assertThat(PlanJson.queries(researchBody(sid, id))).hasSize(6)
            .allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
    }

    // #13
    @Test
    void emptyAnswersEverywhereAreNotAFailure() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        // FR-53: a run whose search found nothing still commits sourcesKept 0, every candidatesConsidered 0 and every sourceIds []
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS.replace("\"searches\":0", "\"searches\":6").replace("}", ",\"sourcesKept\":0}")));
        assertThat(PlanJson.pipelines(researchBody(sid, id))).hasSize(2).allSatisfy(p -> {
            assertThat(p.get("candidatesConsidered")).isEqualTo(0);
            assertThat(p.get("sourceIds")).isEqualTo(List.of());
        });
        assertThat(news.requests).as("one request per planned query").hasSize(6);
        assertThat(PlanJson.queries(researchBody(sid, id))).hasSize(6).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("EMPTY");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
        assertThat(json(getSources(sid, id))).isEqualTo(json("{\"items\":[]}"));
    }

    static Stream<Arguments> emptyFeeds() {
        return Stream.of(
            Arguments.of("empty channel", "<rss version=\"2.0\"><channel></channel></rss>"),
            Arguments.of("channel with a title only", "<rss version=\"2.0\"><channel><title>Google News</title></channel></rss>"),
            Arguments.of("xml declaration and no items", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>x</title><link>https://news.google.com</link></channel></rss>"));
    }

    // #13: the other EMPTY shapes
    @ParameterizedTest(name = "EMPTY answer: {0}")
    @MethodSource("emptyFeeds")
    void emptyShapesAreEmptyNotFailed(String name, String body) throws Exception {
        news.responder = req -> StubNews.rssBody(body);
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as(name).isEqualTo("COMPLETED");
        assertThat(PlanJson.queries(researchBody(sid, id))).hasSize(3).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
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
        news.responder = req -> StubNews.rss(item("s" + req.number()));
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
