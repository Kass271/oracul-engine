package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.reasoning.ScenarioFixtures;
import com.oracul.app.research.StubResponses;
import com.oracul.app.result.AbstractStoryIT;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** generation-runs.md "Slice 17_alternative-future" AlternativeRunIT rows 1 and 3-12 (row 2 is AlternativeRunStagesIT). */
// @trace FR-30, FR-45
class AlternativeRunIT extends AbstractStoryIT {

    static final String NOT_COMPLETED_MSG = "Only a completed future can have an alternative";
    static final String ACTIVE_MSG = "A generation is already running";
    static final String NOT_CONNECTED_MSG = "Connect ChatGPT to generate";
    static final String EXPIRED_MSG = "ChatGPT session expired — please reconnect";
    static final String NOT_ELIGIBLE_MSG = "Your ChatGPT plan is not eligible for ORACUL";
    static final String NOT_DISTINCT_MSG = "ORACUL could not find a different future — try changing a setting";
    static final String ALT_TASK =
        "Follow a different causal path than every future listed in futures-to-avoid; do not paraphrase them.";
    static final String DUP_TASK = "Your previous scenario repeated a future listed in futures-to-avoid. Return a new complete "
        + "scenario with a different future event and at least one different causal step; the problems are listed in duplicate-future.";
    static final String PARENT_BLOCK = "Future 1: Stub future A\n- Stub fact citing E001.\n- Stub inference.\n- Stub speculation.\n- Stub future event.";

    protected ResultActions alt(String sid, String runId) throws Exception {
        var b = post("/api/runs/" + runId + "/alternatives");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    private Map<String, Object> altOk(String sid, String runId) throws Exception {
        MvcResult r = alt(sid, runId).andReturn();
        assertThat(r.getResponse().getStatus()).as("startAlternativeRun: " + r.getResponse().getContentAsString()).isEqualTo(202);
        return json(r.getResponse().getContentAsString());
    }

    private Ran awaitAlt(String sid, String id) throws Exception {
        return new Ran(sid, id, awaitRun(sid, id, 15_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status"))));
    }

    /** P: a standard run awaited COMPLETED with headline. */
    private Ran parent() throws Exception {
        Ran p = runV4(A);
        assertStoryCompleted(p.run());
        return p;
    }

    private Ran altRun(Ran p) throws Exception {
        Map<String, Object> created = altOk(p.sid(), p.id());
        return awaitAlt(p.sid(), (String) created.get("id"));
    }

    private Map<String, Object> row(String id) {
        return jdbc.queryForMap("select status, headline, cast(configuration as text) cfg, cast(counts as text) counts, final_attempt, "
            + "updated_at, completed_at from generation_run where id = cast(? as uuid)", id);
    }

    private List<StubResponses.Request> after(String purpose, int before) {
        List<StubResponses.Request> all = requests(purpose);
        return all.subList(before, all.size());
    }

    private static String texts(StubResponses.Request r) {
        return r.inputText();
    }

    private static List<String> futureLines(String text) {
        return StubResponses.dataBlock(text, "futures-to-avoid").lines().filter(l -> l.startsWith("Future ")).toList();
    }

    private UUID seed(String sid, String status, Integer finalAttempt, String packId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, headline, "
                + "final_attempt, evidence_pack_id, deadline_at, created_at, updated_at, completed_at) values (?, ?, cast(? as uuid), "
                + "'STANDARD', ?, cast(? as jsonb), cast(? as jsonb), ?, ?, cast(? as uuid), ?, ?, ?, ?)",
            id, "ORC-AL-" + id.toString().replace("-", "").substring(20), sid, status, A, ZERO_COUNTS,
            "COMPLETED".equals(status) ? "Seeded headline" : null, finalAttempt, packId,
            now.plusDays(1), now.minusMinutes(5), now.minusMinutes(4), "COMPLETED".equals(status) ? now.minusMinutes(4) : null);
        return id;
    }

    private String packId(Ran r) {
        return jdbc.queryForObject("select cast(evidence_pack_id as text) from generation_run where id = cast(? as uuid)", String.class, r.id());
    }

    // #1 (acceptance 1 + 2): API row #1
    @Test
    void anAlternativeIsQueuedWithTheParentsConfigurationPackAndCounts() throws Exception {
        Ran p = parent();
        Map<String, Object> pRun = p.run();
        int rows = runCount();
        Map<String, Object> created = altOk(p.sid(), p.id());
        assertThat(runCount()).isEqualTo(rows + 1);
        assertThat(created.get("id")).isNotEqualTo(p.id());
        assertThat((String) created.get("generationId")).matches("^ORC-\\d{4}-\\d{2}-\\d{2}-\\d{4}(-\\d+)?$");
        assertThat(created.get("generationId")).isNotEqualTo(pRun.get("generationId"));
        assertThat(created.get("kind")).isEqualTo("ALTERNATIVE");
        assertThat(created.get("parentRunId")).isEqualTo(p.id());
        assertThat(created.get("status")).isEqualTo("QUEUED");
        assertThat(created.get("stageIndex")).isEqualTo(0);
        assertThat(created.get("stageCount")).isEqualTo(10);
        for (String k : List.of("stage", "stageLabel", "failure", "suggestedRealism", "headline", "completedAt")) {
            assertThat(absent(created, k)).as(k + " absent").isTrue();
        }
        assertThat(created.get("configuration")).isEqualTo(pRun.get("configuration"));
        assertThat(created.get("evidencePackId")).isEqualTo(pRun.get("evidencePackId"));
        assertThat(created.get("counts")).isEqualTo(pRun.get("counts"));
        assertThat(created.get("hasOpenCriticIssues")).isEqualTo(false);
        assertThat(OffsetDateTime.parse((String) created.get("updatedAt"))).isEqualTo(OffsetDateTime.parse((String) created.get("createdAt")));
        Map<String, Object> stored = jdbc.queryForMap("select kind, cast(parent_run_id as text) parent, cast(session_id as text) sid, "
            + "cast(evidence_pack_id as text) pack, cast(configuration as text) cfg, cast(counts as text) counts, "
            + "cast(research_profile as text) rp, cast(search_plan as text) sp, extract(epoch from (deadline_at - created_at)) secs "
            + "from generation_run where id = cast(? as uuid)", created.get("id"));
        Map<String, Object> parentRow = jdbc.queryForMap("select cast(configuration as text) cfg, cast(counts as text) counts, "
            + "cast(research_profile as text) rp, cast(search_plan as text) sp, cast(evidence_pack_id as text) pack "
            + "from generation_run where id = cast(? as uuid)", p.id());
        assertThat(stored.get("kind")).isEqualTo("ALTERNATIVE");
        assertThat(stored.get("parent")).isEqualTo(p.id());
        assertThat(stored.get("sid")).isEqualTo(p.sid());
        assertThat(stored.get("pack")).isEqualTo(parentRow.get("pack"));
        assertThat(json((String) stored.get("cfg"))).isEqualTo(json((String) parentRow.get("cfg")));
        assertThat(json((String) stored.get("counts"))).isEqualTo(json((String) parentRow.get("counts")));
        assertThat(jsonOf((String) stored.get("rp"))).isEqualTo(jsonOf((String) parentRow.get("rp")));
        assertThat(jsonOf((String) stored.get("sp"))).isEqualTo(jsonOf((String) parentRow.get("sp")));
        assertThat(((Number) stored.get("secs")).doubleValue()).isEqualTo(180.0);
        awaitAlt(p.sid(), (String) created.get("id"));
    }

    // #1: the run itself
    @Test
    void theAlternativeReusesThePackWithoutSearchingAndAvoidsTheParentFuture() throws Exception {
        Ran p = parent();
        Map<String, Object> before = row(p.id());
        int gdelt0 = gdelt.requests.size();
        int total0 = responses.requests.size();
        int gen0 = requests(GEN).size();
        Ran a = altRun(p);
        assertStoryCompleted(a.run());
        assertThat(a.run().get("kind")).isEqualTo("ALTERNATIVE");
        assertThat(a.run().get("parentRunId")).isEqualTo(p.id());
        assertThat(a.run().get("evidencePackId")).isEqualTo(p.run().get("evidencePackId"));
        List<String> purposes = purposes().subList(total0, purposes().size());
        assertThat(purposes).containsExactly(GEN, "SCENARIO_CRITIC", STORY);
        assertThat(gdelt.requests.size()).isEqualTo(gdelt0);
        String g = after(GEN, gen0).get(0).inputText();
        assertThat(g).contains("Attempt: 1 | Reason: INITIAL");
        assertThat(g).contains(ALT_TASK);
        assertThat(StubResponses.dataBlock(g, "evidence-pack")).isEqualTo(pack(p).get("promptText"));
        assertThat(StubResponses.dataBlock(g, "futures-to-avoid")).isEqualTo(PARENT_BLOCK);
        assertThat(pack(a)).isEqualTo(pack(p));
        Map<String, Object> altScenario = map(structured(a).get("structuredScenario"));
        Map<String, Object> pScenario = map(structured(p).get("structuredScenario"));
        assertThat(map(altScenario.get("futureEvent")).get("title")).isEqualTo("Stub alternative future 1");
        assertThat(map(pScenario.get("futureEvent")).get("title")).isEqualTo("Stub future A");
        List<String> altChain = list(altScenario.get("causalChain")).stream().map(s -> (String) s.get("statement")).toList();
        List<String> pChain = list(pScenario.get("causalChain")).stream().map(s -> (String) s.get("statement")).toList();
        assertThat(altChain).contains("Stub alternative speculation 1.");
        assertThat(pChain).doesNotContain("Stub alternative speculation 1.");
        Map<String, Object> expectedCounts = new LinkedHashMap<>(counts(p.run()));
        expectedCounts.put("sourcesUsed", 1);
        assertThat(counts(a.run())).isEqualTo(expectedCounts);
        Map<String, Object> research = researchBody(a.sid(), a.id());
        Map<String, Object> pResearch = researchBody(p.sid(), p.id());
        assertThat(research.get("researchProfile")).isEqualTo(pResearch.get("researchProfile"));
        assertThat(research.get("searchPlan")).isEqualTo(pResearch.get("searchPlan"));
        assertThat(research.get("runId")).isEqualTo(a.id());
        assertThat(result(a).get("sources")).isEqualTo(result(p).get("sources"));
        Map<String, Object> after = row(p.id());
        assertThat(after.get("status")).isEqualTo(before.get("status"));
        assertThat(after.get("headline")).isEqualTo(before.get("headline"));
        assertThat(json((String) after.get("cfg"))).isEqualTo(json((String) before.get("cfg")));
        assertThat(json((String) after.get("counts"))).isEqualTo(json((String) before.get("counts")));
        assertThat(after.get("final_attempt")).isEqualTo(before.get("final_attempt"));
        assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
        assertThat(after.get("completed_at")).isEqualTo(before.get("completed_at"));
        assertThat(sourceItems(a.sid(), a.id())).isEqualTo(sourceItems(p.sid(), p.id()));
    }

    // #3 (API row #2 + futures order)
    @Test
    void anAlternativeOfAnAlternativeListsTheParentFirstAndKeepsTheRootPack() throws Exception {
        Ran p = parent();
        Ran a1 = altRun(p);
        assertStoryCompleted(a1.run());
        int gen0 = requests(GEN).size();
        Map<String, Object> created = altOk(a1.sid(), a1.id());
        assertThat(created.get("parentRunId")).isEqualTo(a1.id());
        assertThat(created.get("evidencePackId")).isEqualTo(p.run().get("evidencePackId"));
        Ran a2 = awaitAlt(a1.sid(), (String) created.get("id"));
        assertStoryCompleted(a2.run());
        assertThat(futureLines(after(GEN, gen0).get(0).inputText()))
            .containsExactly("Future 1: Stub alternative future 1", "Future 2: Stub future A");
        assertThat(map(map(structured(a2).get("structuredScenario")).get("futureEvent")).get("title")).isEqualTo("Stub alternative future 2");
        assertThat(a2.run().get("evidencePackId")).isEqualTo(p.run().get("evidencePackId"));
    }

    // #4
    @Test
    void aRepeatedFutureIsRegeneratedOnceWithTheDuplicateNamed() throws Exception {
        Ran p = parent();
        int gen0 = requests(GEN).size();
        int critic0 = requests("SCENARIO_CRITIC").size();
        scriptScenario(computed(ScenarioFixtures::scenarioDefault));
        Ran a = altRun(p);
        assertStoryCompleted(a.run());
        List<StubResponses.Request> gens = after(GEN, gen0);
        assertThat(gens).hasSize(2);
        assertThat(gens.stream().map(r -> StubResponses.attemptReason(r.inputText())).toList()).containsExactly("INITIAL", "ALTERNATIVE_DISTINCT");
        String g2 = gens.get(1).inputText();
        assertThat(g2).contains("Attempt: 2 | Reason: ALTERNATIVE_DISTINCT").contains(ALT_TASK).contains(DUP_TASK);
        assertThat(StubResponses.dataBlock(g2, "duplicate-future"))
            .isEqualTo("Rejected future: Stub future A\nsame future event title as future 1\nsame causal steps as future 1");
        assertThat(StubResponses.dataBlock(g2, "futures-to-avoid")).isEqualTo(PARENT_BLOCK);
        List<StubResponses.Request> critics = after("SCENARIO_CRITIC", critic0);
        assertThat(critics).hasSize(2);
        assertThat(critics.get(0).inputText()).contains("Attempt: 1");
        assertThat(critics.get(1).inputText()).contains("Attempt: 2");
        assertThat(finalAttempt(a.id())).isEqualTo(2);
    }

    // #5
    @Test
    void aSecondRepeatFailsTheRunWithAlternativeNotDistinct() throws Exception {
        Ran p = parent();
        int gen0 = requests(GEN).size();
        int story0 = requests(STORY).size();
        always(GEN, computed(ScenarioFixtures::scenarioDefault));
        Ran a = altRun(p);
        assertFailed(a.run(), "ALTERNATIVE_NOT_DISTINCT", NOT_DISTINCT_MSG, "CONSTRUCTING_SCENARIO", 9);
        assertThat(absent(a.run(), "headline")).isTrue();
        assertThat(after(GEN, gen0)).hasSize(2);
        assertThat(after(STORY, story0)).isEmpty();
        assertThat(finalAttempt(a.id())).isNull();
        assertResultNotReady(getResult(a.sid(), a.id()));
        startOk(a.sid(), A); // the slot is released
    }

    // #6
    @Test
    void aRepeatedTitleWithOtherCaseAndSpacingIsNamedAloneInTheDuplicateBlock() throws Exception {
        Ran p = parent();
        int gen0 = requests(GEN).size();
        scriptScenario(computed(in -> ScenarioFixtures.scenarioAlternative(in, 1)
            .replace("\"Stub alternative future 1\"", "\"  stub FUTURE a \"")));
        Ran a = altRun(p);
        assertStoryCompleted(a.run());
        List<StubResponses.Request> gens = after(GEN, gen0);
        assertThat(gens).hasSize(2);
        assertThat(StubResponses.attemptReason(gens.get(1).inputText())).isEqualTo("ALTERNATIVE_DISTINCT");
        String dup = StubResponses.dataBlock(gens.get(1).inputText(), "duplicate-future");
        assertThat(dup).contains("same future event title as future 1").doesNotContain("same causal steps");
        assertThat(dup.lines().skip(1).toList()).containsExactly("same future event title as future 1");
    }

    // #7
    @Test
    void repeatedCausalStepsAreNamedAloneInTheDuplicateBlock() throws Exception {
        Ran p = parent();
        int gen0 = requests(GEN).size();
        scriptScenario(computed(in -> ScenarioFixtures.scenarioDefault(in).replace("\"Stub future A\"", "\"Stub alternative future 1\"")));
        Ran a = altRun(p);
        assertStoryCompleted(a.run());
        List<StubResponses.Request> gens = after(GEN, gen0);
        assertThat(gens).hasSize(2);
        assertThat(StubResponses.attemptReason(gens.get(1).inputText())).isEqualTo("ALTERNATIVE_DISTINCT");
        assertThat(StubResponses.dataBlock(gens.get(1).inputText(), "duplicate-future"))
            .isEqualTo("Rejected future: Stub alternative future 1\nsame causal steps as future 1");
    }

    // #8
    @Test
    void anInvalidAnswerAfterTheDistinctRegenerationFailsWithoutASecondSchemaCorrection() throws Exception {
        Ran p = parent();
        int gen0 = requests(GEN).size();
        scriptScenario(text("not json"), computed(ScenarioFixtures::scenarioDefault), text("not json"));
        Ran a = altRun(p);
        assertFailed(a.run(), "INVALID_SCENARIO", INVALID, "CONSTRUCTING_SCENARIO", 9);
        List<StubResponses.Request> gens = after(GEN, gen0);
        assertThat(gens.stream().map(r -> StubResponses.attemptReason(r.inputText())).toList())
            .containsExactly("INITIAL", "SCHEMA_CORRECTION", "ALTERNATIVE_DISTINCT");
    }

    // #9
    @Test
    void aRateLimitedGenerationFailsInStageExploringFuturesAndLeavesTheParentAlone() throws Exception {
        Ran p = parent();
        Map<String, Object> before = row(p.id());
        always(GEN, StubResponses.status(429, PROVIDER_BODY));
        Ran a = altRun(p);
        assertFailed(a.run(), "CHATGPT_RATE_LIMITED", "ChatGPT usage limit reached — try again later", "EXPLORING_FUTURES", 7);
        assertThat(row(p.id())).isEqualTo(before);
    }

    // #10 row #3
    @ParameterizedTest(name = "unknown run [{0}]")
    @ValueSource(strings = {NO_RUN, "abc"})
    void anUnknownOrMalformedRunIs404(String id) throws Exception {
        String sid = connectedSid();
        int rows = runCount();
        assertError(alt(sid, id), 404, "RUN_NOT_FOUND", "Future not found");
        assertThat(runCount()).isEqualTo(rows);
    }

    // #10 row #3
    @Test
    void aForeignRunIs404WithAndWithoutCookieEvenForAnUnconnectedSession() throws Exception {
        Ran p = parent();
        int rows = runCount();
        assertError(alt(connectedSid(), p.id()), 404, "RUN_NOT_FOUND", "Future not found");
        assertError(alt(newSid(), p.id()), 404, "RUN_NOT_FOUND", "Future not found");
        assertError(alt(null, p.id()), 404, "RUN_NOT_FOUND", "Future not found");
        assertThat(runCount()).isEqualTo(rows);
    }

    // #10 row #4
    @Test
    void aDisconnectedSessionIs401AndStoresNothing() throws Exception {
        Ran p = parent();
        disconnect(p.sid());
        int rows = runCount();
        assertError(alt(p.sid(), p.id()), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
        assertThat(runCount()).isEqualTo(rows);
    }

    // #10 row #5
    @Test
    void aFailedRefreshIs401SessionExpired() throws Exception {
        Ran source = parent();
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? com.oracul.app.chatgpt.StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}")
            : stub.ok(30, com.oracul.app.chatgpt.StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        UUID p = seed(sid, "COMPLETED", 1, packId(source));
        int rows = runCount();
        assertError(alt(sid, p.toString()), 401, "CHATGPT_SESSION_EXPIRED", EXPIRED_MSG);
        assertThat(runCount()).isEqualTo(rows);
    }

    // #10 row #6
    @Test
    void aPlanThatIsNotEligibleIs403() throws Exception {
        Ran source = parent();
        stub.responder = r -> stub.ok(3600, "openid", true);
        String sid = connectedSid();
        UUID p = seed(sid, "COMPLETED", 1, packId(source));
        int rows = runCount();
        assertError(alt(sid, p.toString()), 403, "CHATGPT_PLAN_NOT_ELIGIBLE", NOT_ELIGIBLE_MSG);
        assertThat(runCount()).isEqualTo(rows);
    }

    // #10 row #7 (parameterized over every non-completed state and both missing links)
    @ParameterizedTest(name = "parent {0}")
    @ValueSource(strings = {"FAILED", "INSUFFICIENT_EVIDENCE", "STOPPED", "QUEUED", "RUNNING", "COMPLETED-no-final-attempt", "COMPLETED-no-pack"})
    void aParentThatIsNotCompletedIs409(String kind) throws Exception {
        Ran source = parent();
        String sid = connectedSid();
        UUID p = switch (kind) {
            case "COMPLETED-no-final-attempt" -> seed(sid, "COMPLETED", null, packId(source));
            case "COMPLETED-no-pack" -> seed(sid, "COMPLETED", 1, null);
            default -> seed(sid, kind, null, null);
        };
        int rows = runCount();
        try {
            assertError(alt(sid, p.toString()), 409, "RUN_NOT_COMPLETED", NOT_COMPLETED_MSG);
            assertThat(runCount()).isEqualTo(rows);
        } finally {
            jdbc.update("update generation_run set status = 'FAILED' where id = ? and status in ('QUEUED','RUNNING')", p);
        }
    }

    // #10 row #8
    @Test
    void anActiveRunOfTheSessionIs409() throws Exception {
        Ran p = parent();
        UUID active = seed(p.sid(), "QUEUED", null, null);
        int rows = runCount();
        try {
            assertError(alt(p.sid(), p.id()), 409, "RUN_ALREADY_ACTIVE", ACTIVE_MSG);
            assertThat(runCount()).isEqualTo(rows);
        } finally {
            jdbc.update("update generation_run set status = 'FAILED' where id = ?", active);
        }
    }

    // #10 row #9
    @Test
    void concurrentAlternativesYieldExactlyOneRun() throws Exception {
        Ran p = parent();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> ids = new ArrayList<>();
        try {
            Callable<String[]> call = () -> {
                go.await();
                var r = alt(p.sid(), p.id()).andReturn().getResponse();
                return new String[] {String.valueOf(r.getStatus()), r.getContentAsString()};
            };
            List<Future<String[]>> fs = List.of(pool.submit(call), pool.submit(call));
            go.countDown();
            List<String> statuses = new ArrayList<>();
            int conflicts = 0;
            for (Future<String[]> f : fs) {
                String[] r = f.get();
                statuses.add(r[0]);
                if (r[1].contains("RUN_ALREADY_ACTIVE")) conflicts++;
                if ("202".equals(r[0])) ids.add((String) json(r[1]).get("id"));
            }
            assertThat(statuses).containsExactlyInAnyOrder("202", "409");
            assertThat(conflicts).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        Integer active = jdbc.queryForObject(
            "select count(*) from generation_run where cast(session_id as text) = ? and status in ('QUEUED','RUNNING')", Integer.class, p.sid());
        assertThat(active).isLessThanOrEqualTo(1);
        awaitAlt(p.sid(), ids.get(0));
    }

    // #10 row #10
    @Test
    void twoSequentialAlternativesOfTheSameParentBothPointAtIt() throws Exception {
        Ran p = parent();
        Ran a1 = altRun(p);
        Ran a2 = altRun(p);
        for (Ran a : List.of(a1, a2)) {
            assertThat(a.run().get("parentRunId")).isEqualTo(p.id());
            assertThat(a.run().get("evidencePackId")).isEqualTo(p.run().get("evidencePackId"));
        }
        assertThat(a2.id()).isNotEqualTo(a1.id());
    }

    // #11
    @Test
    @SuppressWarnings("unchecked")
    void theRecentFuturesListContainsTheAlternativeFirst() throws Exception {
        Ran p = parent();
        Ran a = altRun(p);
        assertStoryCompleted(a.run());
        MvcResult r = mvc.perform(get("/api/runs").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> items = (List<Map<String, Object>>) json(r.getResponse().getContentAsString()).get("items");
        assertThat(items.stream().map(i -> (String) i.get("id")).toList()).containsExactly(a.id(), p.id());
        assertThat(items.get(0).get("kind")).isEqualTo("ALTERNATIVE");
        assertThat(items.get(0).get("headline")).isEqualTo(com.oracul.app.result.StoryFixtures.HEADLINE);
    }

    // #12
    @Test
    void aStandardRunAfterAnAlternativeCarriesNoAlternativeContext() throws Exception {
        Ran p = parent();
        altRun(p);
        int gen0 = requests(GEN).size();
        int exp0 = requests(EXPANSION).size();
        gdelt.reset();
        gdeltArticles(v4());
        Map<String, Object> created = startOk(p.sid(), A);
        awaitDone(p.sid(), (String) created.get("id"));
        List<StubResponses.Request> gens = after(GEN, gen0);
        assertThat(gens).isNotEmpty();
        for (StubResponses.Request g : gens) {
            assertThat(g.inputText()).doesNotContain("futures-to-avoid").doesNotContain("duplicate-future").doesNotContain(ALT_TASK);
        }
        assertThat(after(EXPANSION, exp0)).isNotEmpty();
    }
}
