package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.research.StubNews;
import com.oracul.app.research.StubResponses;
import com.oracul.app.result.AbstractStoryIT;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * run-control.md FR-45 "Stop a generation and start a new one", backend half: POST /api/runs/{runId}/stop over every status
 * and runId class (seeded rows), and the stop during every kind of outbound request of the pipeline (the stub holds the
 * request, the run is stopped, the stub releases it: nothing more may be sent or written for the run).
 */
// @trace FR-45
class StopRunIT extends AbstractStoryIT {

    private static final String PROFILE = "{\"darkness\":0.5,\"optimism\":0.5,\"realism\":0.8,\"horizon\":\"1y\",\"topics\":[]}";

    private static final String[] STAGES = {"UNDERSTANDING", "RESEARCH_STRATEGY", "SEARCHING", "READING_SOURCES",
        "CONNECTING_SIGNALS", "RANKING", "EXPLORING_FUTURES", "CHALLENGING_ASSUMPTIONS", "CONSTRUCTING_SCENARIO", "WRITING_STORY"};

    // ---- helpers ------------------------------------------------------------------------------------------------

    private ResultActions stop(String sid, String runId) throws Exception {
        var b = post("/api/runs/" + runId + "/stop");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    private Map<String, Object> stopOk(String sid, String runId) throws Exception {
        MvcResult r = stop(sid, runId).andReturn();
        assertThat(r.getResponse().getStatus()).as("stopRun: " + r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r.getResponse().getContentAsString());
    }

    /** Seeds a run row of the session in the given status (stage may be null). */
    private UUID seed(String sid, String status, String stage) {
        UUID id = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        boolean terminal = !"QUEUED".equals(status) && !"RUNNING".equals(status);
        String failureCode = "FAILED".equals(status) ? "CHATGPT_UNAVAILABLE" : "INSUFFICIENT_EVIDENCE".equals(status) ? "INSUFFICIENT_EVIDENCE" : null;
        String failureMessage = "FAILED".equals(status) ? "ChatGPT is temporarily unavailable — try again in a few minutes"
            : "INSUFFICIENT_EVIDENCE".equals(status) ? "ORACUL found insufficient current evidence to construct this scenario at Realism 8." : null;
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, stage, configuration, counts, "
                + "failure_code, failure_message, headline, deadline_at, created_at, updated_at, completed_at) "
                + "values (?, ?, cast(? as uuid), 'STANDARD', ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?)",
            id, "ORC-STP-" + id.toString().replace("-", "").substring(20), sid, status, stage, B, ZERO_COUNTS,
            failureCode, failureMessage, "COMPLETED".equals(status) ? "Seeded headline" : null,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), created, created, terminal ? created.plusSeconds(30) : null);
        return id;
    }

    private Map<String, Object> rowOf(UUID id) {
        return jdbc.queryForMap("select * from generation_run where id = ?", id);
    }

    private void freeSlot(String sid) {
        jdbc.update("update generation_run set status = 'FAILED' where cast(session_id as text) = ? and status in ('QUEUED','RUNNING')", sid);
    }

    private static boolean awaitTrue(BooleanSupplier condition, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    // ---- status classes (seeded rows) ---------------------------------------------------------------------------

    static Stream<Arguments> statusClasses() {
        List<Arguments> out = new ArrayList<>();
        out.add(Arguments.of("QUEUED", null));
        for (String stage : STAGES) out.add(Arguments.of("RUNNING", stage));
        out.add(Arguments.of("COMPLETED", "WRITING_STORY"));
        out.add(Arguments.of("FAILED", "EXPLORING_FUTURES"));
        out.add(Arguments.of("INSUFFICIENT_EVIDENCE", "RANKING"));
        out.add(Arguments.of("STOPPED", "SEARCHING"));
        return out.stream();
    }

    // every RunStatus value is covered: QUEUED, RUNNING (at each of the 10 stages), COMPLETED, FAILED, INSUFFICIENT_EVIDENCE, STOPPED
    @ParameterizedTest(name = "stop of a {0} run at stage {1}")
    @MethodSource("statusClasses")
    void stopRunHandlesEveryStatusClass(String status, String stage) throws Exception {
        String sid = newSid();
        UUID id = seed(sid, status, stage);
        try {
            Map<String, Object> before = json(getRun(sid, id.toString()));
            Map<String, Object> rowBefore = rowOf(id);
            Map<String, Object> body = stopOk(sid, id.toString());
            boolean active = "QUEUED".equals(status) || "RUNNING".equals(status);
            if (active) {
                assertThat(body.get("status")).as("body: " + body).isEqualTo("STOPPED");
                assertThat(body.get("id")).isEqualTo(id.toString());
                assertThat(body.get("completedAt")).as("completedAt = the stop time").isNotNull();
                assertThat(body.get("failure")).as("a stop is not a failure").isNull();
                assertThat(body.get("headline")).isNull();
                assertThat(body.get("evidenceNote")).isNull();
                if (stage == null) {
                    assertThat(body.get("stageIndex")).as("stopped while QUEUED").isEqualTo(0);
                    assertThat(body.get("stage")).isNull();
                } else {
                    assertThat(body.get("stage")).as("stage of the moment of the stop").isEqualTo(stage);
                    assertThat(body.get("stageIndex")).isEqualTo(List.of(STAGES).indexOf(stage) + 1);
                }
                Map<String, Object> row = rowOf(id);
                assertThat(row.get("status")).isEqualTo("STOPPED");
                assertThat(row.get("completed_at")).isNotNull();
                assertThat(row.get("failure_code")).isNull();
                assertThat(row.get("headline")).isNull();
                assertThat(row.get("stage")).isEqualTo(stage);
                assertThat(json(getRun(sid, id.toString()))).as("getRun after the stop").isEqualTo(body);
                assertThat(jdbc.queryForObject("select count(*) from generation_run where cast(session_id as text) = ? "
                    + "and status in ('QUEUED','RUNNING')", Integer.class, sid)).as("the active-run slot is free").isZero();
            } else {
                assertThat(body).as("a terminal run is not changed by a stop request").isEqualTo(before);
                assertThat(rowOf(id)).as("every column of the row stays").isEqualTo(rowBefore);
            }
        } finally {
            freeSlot(sid);
        }
    }

    // ---- runId classes -----------------------------------------------------------------------------------------

    @ParameterizedTest(name = "stop of {0} is 404 RUN_NOT_FOUND")
    @ValueSource(strings = {"unknown-uuid", "abc", "12345", "foreign-run", "no-cookie"})
    void unknownMalformedAndForeignRunsAre404(String kind) throws Exception {
        String sid = newSid();
        String other = newSid();
        UUID foreign = seed(other, "COMPLETED", "WRITING_STORY");
        Map<String, Object> rowBefore = rowOf(foreign);
        ResultActions r = switch (kind) {
            case "unknown-uuid" -> stop(sid, NO_RUN);
            case "foreign-run" -> stop(sid, foreign.toString());
            case "no-cookie" -> stop(null, foreign.toString());
            default -> stop(sid, kind);
        };
        assertError(r, 404, "RUN_NOT_FOUND", "Future not found");
        assertThat(rowOf(foreign)).as("a foreign run is never touched").isEqualTo(rowBefore);
    }

    @Test
    void anOwnRunIs200AndAForeignActiveRunStaysActive() throws Exception {
        String sid = newSid();
        String other = newSid();
        UUID foreign = seed(other, "RUNNING", "SEARCHING");
        UUID own = seed(sid, "RUNNING", "SEARCHING");
        try {
            assertError(stop(sid, foreign.toString()), 404, "RUN_NOT_FOUND", "Future not found");
            assertThat(rowOf(foreign).get("status")).isEqualTo("RUNNING");
            assertThat(stopOk(sid, own.toString()).get("status")).isEqualTo("STOPPED");
            assertThat(rowOf(own).get("status")).isEqualTo("STOPPED");
        } finally {
            freeSlot(sid);
            freeSlot(other);
        }
    }

    @Test
    void stopNeedsNoChatGptConnectionAndIgnoresABody() throws Exception {
        String sid = connectedSid();
        UUID id = seed(sid, "RUNNING", "READING_SOURCES");
        try {
            disconnect(sid).andReturn();
            MvcResult r = mvc.perform(post("/api/runs/" + id + "/stop").cookie(new Cookie("ORACUL_SID", sid))
                .contentType("application/json").content("{\"ignored\":true}")).andReturn();
            assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(json(r.getResponse().getContentAsString()).get("status")).isEqualTo("STOPPED");
        } finally {
            freeSlot(sid);
        }
    }

    @Test
    void stoppingTwiceGivesTheSameAnswer() throws Exception {
        String sid = newSid();
        UUID id = seed(sid, "RUNNING", "RANKING");
        try {
            Map<String, Object> first = stopOk(sid, id.toString());
            Map<String, Object> rowAfterFirst = rowOf(id);
            Map<String, Object> second = stopOk(sid, id.toString());
            assertThat(second).isEqualTo(first);
            assertThat(rowOf(id)).isEqualTo(rowAfterFirst);
        } finally {
            freeSlot(sid);
        }
    }

    // ---- what a STOPPED run serves ----------------------------------------------------------------------------

    @Test
    void aStoppedRunServesNoResultScenarioOrAlternativeButItsStoredParts() throws Exception {
        String sid = connectedSid();
        UUID id = seed(sid, "RUNNING", "SEARCHING");
        // a real run at SEARCHING always has its research profile (FR-45: research as stored)
        jdbc.update("update generation_run set research_profile = cast(? as jsonb) where id = ?", PROFILE, id);
        try {
            stopOk(sid, id.toString());
            assertResultNotReady(getResult(sid, id.toString()));
            assertScenarioNotReady(getStructured(sid, id.toString()));
            assertNotReady(getPack(sid, id.toString()));
            assertError(mvc.perform(post("/api/runs/" + id + "/alternatives").cookie(new Cookie("ORACUL_SID", sid))),
                409, "RUN_NOT_COMPLETED", "Only a completed future can have an alternative");
            assertThat(getResearch(sid, id.toString()).andReturn().getResponse().getStatus()).isEqualTo(200);
            assertThat(sourceItems(sid, id.toString())).isEmpty();
            assertThat(getEvents(sid, id.toString()).andReturn().getResponse().getStatus()).isEqualTo(200);
        } finally {
            freeSlot(sid);
        }
    }

    @Test
    void resetChatGptConnectionIsAllowedOnceTheRunIsStopped() throws Exception {
        String sid = connectedSid();
        UUID id = seed(sid, "RUNNING", "RESEARCH_STRATEGY");
        try {
            MvcResult refused = mvc.perform(delete("/api/auth/chatgpt/registration").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
            assertThat(refused.getResponse().getStatus()).as("reset refuses an active run").isEqualTo(409);
            stopOk(sid, id.toString());
            MvcResult ok = mvc.perform(delete("/api/auth/chatgpt/registration").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
            assertThat(ok.getResponse().getStatus()).as(ok.getResponse().getContentAsString()).isEqualTo(204);
        } finally {
            freeSlot(sid);
        }
    }

    // ---- stop during every kind of outbound request -----------------------------------------------------------

    /** A held outbound request of the pipeline: install the hold, wait for its arrival, open it again. */
    private final class Hold {
        final String kind;
        final CountDownLatch open = new CountDownLatch(1);
        final CountDownLatch arrived = new CountDownLatch(1);

        Hold(String kind) {
            this.kind = kind;
        }

        void install() {
            switch (kind) {
                case "MODELS" -> responses.modelsResponder = req -> {
                    arrived.countDown();
                    await();
                    return new StubResponses.Reply(200, StubResponses.DEFAULT_CATALOGUE, 0);
                };
                case "GOOGLE" -> {
                    var inner = news.responder;
                    news.responder = req -> {
                        arrived.countDown();
                        await();
                        return inner.apply(req); // an answer that arrives after a stop must not be used
                    };
                }
                case "ARTICLE" -> news.articleGate = open;
                default -> responses.gate(kind);
            }
        }

        boolean awaitArrival() throws InterruptedException {
            return switch (kind) {
                case "MODELS", "GOOGLE" -> arrived.await(15, TimeUnit.SECONDS);
                // fetch concurrency is 8: wait until all permits are taken, the other fetches of the 12 articles are queued
                case "ARTICLE" -> awaitTrue(() -> news.articleRequests.size() >= 8, 15_000);
                default -> responses.awaitArrived(kind, 1, Duration.ofSeconds(15));
            };
        }

        void release() {
            open.countDown();
            if (!"ARTICLE".equals(kind) && !List.of("MODELS", "GOOGLE").contains(kind)) responses.release(kind);
        }

        private void await() {
            try {
                open.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<Art> twelveArticles() {
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= 12; i++) arts.add(new Art("stop-" + i, "reuters.com", "Stop article number " + i));
        return arts;
    }

    private Map<String, Integer> traffic() {
        Map<String, Integer> t = new LinkedHashMap<>();
        t.put("responses", responses.requests.size());
        t.put("models", responses.modelRequests.size());
        t.put("token", stub.requests.size());
        t.put("news", news.requests.size());
        t.put("paths", news.paths.size());
        t.put("articles", news.articleRequests.size());
        return t;
    }

    private Map<String, Integer> rowCounts(String runId) {
        Map<String, Integer> c = new LinkedHashMap<>();
        // model_call is not listed: the answer of a request already in flight may still be logged (spec: sources, events, pack, attempts, story)
        for (String table : List.of("source", "event", "evidence_pack", "scenario_attempt", "future_story")) {
            c.put(table, jdbc.queryForObject("select count(*) from " + table + " where run_id = cast(? as uuid)", Integer.class, runId));
        }
        return c;
    }

    static Stream<Arguments> outboundKinds() {
        // kind, EvidencePack readable after the stop, a parsed scenario attempt exists after the stop
        return Stream.of(
            Arguments.of("QUERY_EXPANSION", false, false),
            Arguments.of("MODELS", false, false),
            Arguments.of("GOOGLE", false, false),
            Arguments.of("ARTICLE", false, false),
            Arguments.of("EVENT_NORMALIZATION", false, false),
            Arguments.of("EVENT_CLASSIFICATION", false, false),
            Arguments.of("SCENARIO_GENERATION", true, false),
            Arguments.of("SCENARIO_CRITIC", true, true),
            Arguments.of("STORY_WRITING", true, true));
    }

    @ParameterizedTest(name = "stop while the {0} request is in flight")
    @MethodSource("outboundKinds")
    void aStopDuringAnOutboundRequestEndsTheRunForGood(String kind, boolean packReady, boolean scenarioReady) throws Exception {
        news.reset();
        newsArticles(twelveArticles());
        Hold hold = new Hold(kind);
        hold.install();
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        try {
            assertThat(hold.awaitArrival()).as(kind + " request arrived at the stub").isTrue();

            Map<String, Object> stopped = stopOk(sid, id);
            assertThat(stopped.get("status")).as("stop body: " + stopped).isEqualTo("STOPPED");
            assertThat(stopped.get("failure")).isNull();
            assertThat(stopped.get("headline")).isNull();
            assertThat(stopped.get("completedAt")).isNotNull();
            Map<String, Object> rowAtStop = rowOf(UUID.fromString(id));
            assertThat(rowAtStop.get("status")).isEqualTo("STOPPED");
            Map<String, Integer> trafficAtStop = traffic();
            Map<String, Integer> rowsAtStop = rowCounts(id);

            hold.release();
            long end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end) {
                assertThat(rowOf(UUID.fromString(id))).as("the pipeline must not touch a STOPPED run").isEqualTo(rowAtStop);
                assertThat(rowCounts(id)).as("no row is written for a STOPPED run").isEqualTo(rowsAtStop);
                assertThat(traffic()).as("no new request of any kind arrives for the run after a stop").isEqualTo(trafficAtStop);
                Thread.sleep(100);
            }
            Map<String, Object> finalRun = json(getRun(sid, id));
            assertThat(finalRun.get("status")).isEqualTo("STOPPED");
            assertThat(finalRun.get("stage")).as("stage of the moment of the stop").isEqualTo(stopped.get("stage"));
            assertThat(finalRun.get("failure")).isNull();

            // endpoints of a STOPPED run
            assertResultNotReady(getResult(sid, id));
            if (packReady) {
                assertThat(getPack(sid, id).andReturn().getResponse().getStatus()).as("RANKING had committed").isEqualTo(200);
            } else {
                assertNotReady(getPack(sid, id));
            }
            if (scenarioReady) {
                assertThat(getStructured(sid, id).andReturn().getResponse().getStatus()).as("an attempt was parsed").isEqualTo(200);
            } else {
                assertScenarioNotReady(getStructured(sid, id));
            }

            // the slot is free: a new run is accepted at once
            assertThat(startRun(sid, B).andReturn().getResponse().getStatus()).as("a new run after a stop").isEqualTo(202);
        } finally {
            hold.release();
        }
    }

    // ---- a stop racing the end of the pipeline -------------------------------------------------------------------

    @Test
    void aStopRacingTheEndOfTheRunEndsEitherCompletedWithAStoryOrStoppedWithoutOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 1; round <= 6; round++) {
                news.reset();
                newsArticles(twelveArticles());
                responses.gate("STORY_WRITING");
                String sid = connectedSid();
                String id = (String) startOk(sid, A).get("id");
                assertThat(responses.awaitArrived("STORY_WRITING", 1, Duration.ofSeconds(15))).isTrue();
                CountDownLatch go = new CountDownLatch(1);
                Future<?> release = pool.submit(() -> {
                    go.await();
                    responses.release("STORY_WRITING");
                    return null;
                });
                Future<Map<String, Object>> stopping = pool.submit(() -> {
                    go.await();
                    return stopOk(sid, id);
                });
                go.countDown();
                release.get(15, TimeUnit.SECONDS);
                Map<String, Object> answer = stopping.get(15, TimeUnit.SECONDS);
                // wait until the pipeline has ended whichever way
                Map<String, Object> end = awaitRun(sid, id, 10_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
                int stories = storyRows(id);
                String status = (String) end.get("status");
                assertThat(status).as("round " + round + " ended " + end).isIn("COMPLETED", "STOPPED");
                if ("COMPLETED".equals(status)) {
                    assertThat(stories).as("COMPLETED has its story").isEqualTo(1);
                    assertThat(end.get("headline")).isNotNull();
                    assertThat(answer.get("status")).as("round " + round + ": the stop that lost the race answers with the COMPLETED body, never STOPPED").isEqualTo("COMPLETED");
                    assertThat(answer.get("headline")).as("round " + round + ": the COMPLETED body carries the final headline").isEqualTo(end.get("headline"));
                } else {
                    assertThat(stories).as("STOPPED has no story").isZero();
                    assertThat(end.get("headline")).isNull();
                    assertThat(answer.get("status")).isEqualTo("STOPPED");
                }
                assertThat(end.get("failure")).as("never FAILED").isNull();
            }
        } finally {
            pool.shutdownNow();
            responses.release("STORY_WRITING");
        }
    }

    // ---- GET paths stay untouched ------------------------------------------------------------------------------

    @Test
    void stopIsAPostOnlyOperation() throws Exception {
        String sid = newSid();
        UUID id = seed(sid, "RUNNING", "SEARCHING");
        try {
            MvcResult r = mvc.perform(get("/api/runs/" + id + "/stop").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
            assertThat(r.getResponse().getStatus()).as("GET on the stop path").isIn(404, 405);
            assertThat(rowOf(id).get("status")).isEqualTo("RUNNING");
        } finally {
            freeSlot(sid);
        }
    }
}
